package com.wtm.net;

import com.wtm.usage.ApiUsageTracker;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Centralized outbound HTTP client.
 *
 * Security rules:
 * - Only HTTPS endpoints are accepted.
 * - API-key-bearing URLs are never included in exception messages.
 * - Response bodies are size-limited before being accumulated in memory.
 * - All calls use explicit connection/request timeouts.
 */
public final class HttpService {
    private static final Duration CONNECT_TIMEOUT=Duration.ofSeconds(8);
    private static final Duration REQUEST_TIMEOUT=Duration.ofSeconds(15);

    private static final int MAX_TEXT_BYTES=2*1024*1024;
    private static final int MAX_BINARY_BYTES=12*1024*1024;

    private static final String UA=
            "WeatherTrafficMonitor/2.9 (workplace-display; contact=local-admin)";

    private static final Pattern HEADER_NAME=
            Pattern.compile("[A-Za-z0-9!#$%&'*+.^_`|~-]+");

    private final HttpClient client=HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String getText(String url) throws IOException,InterruptedException {
        return getText(url,UA);
    }

    /** Allows NWS to use the installation's identifying User-Agent. */
    public String getText(String url,String userAgent)
            throws IOException,InterruptedException {
        URI uri=validatedHttpsUri(url);
        String ua=cleanHeaderValue(userAgent==null||userAgent.isBlank()?UA:userAgent);

        HttpRequest request=HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent",ua)
                .header(
                        "Accept",
                        "application/geo+json, application/json, text/plain, */*"
                )
                .GET()
                .build();

        return sendText(request,uri,url);
    }

    /** Performs a GET with one provider-specific header such as X-API-KEY. */
    public String getTextWithHeader(
            String url,
            String headerName,
            String headerValue
    ) throws IOException,InterruptedException {
        URI uri=validatedHttpsUri(url);

        HttpRequest.Builder builder=HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent",UA)
                .header("Accept","application/json, text/plain, */*")
                .GET();

        if(headerName!=null&&!headerName.isBlank()
                &&headerValue!=null&&!headerValue.isBlank()){
            if(!HEADER_NAME.matcher(headerName).matches())
                throw new IOException("Invalid HTTP header name.");
            builder.header(headerName,cleanHeaderValue(headerValue));
        }

        return sendText(builder.build(),uri,url);
    }

    public byte[] getBytes(String url) throws IOException,InterruptedException {
        URI uri=validatedHttpsUri(url);

        HttpRequest request=HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent",UA)
                .GET()
                .build();

        ApiUsageTracker.get().record(url);
        HttpResponse<InputStream> response=
                client.send(request,HttpResponse.BodyHandlers.ofInputStream());

        verifyStatus(response.statusCode(),uri);

        try(InputStream in=response.body()){
            return readBounded(in,MAX_BINARY_BYTES,provider(uri));
        }
    }

    private String sendText(HttpRequest request,URI uri,String accountingUrl)
            throws IOException,InterruptedException {
        ApiUsageTracker.get().record(accountingUrl);

        HttpResponse<InputStream> response=
                client.send(request,HttpResponse.BodyHandlers.ofInputStream());

        verifyStatus(response.statusCode(),uri);

        try(InputStream in=response.body()){
            return new String(
                    readBounded(in,MAX_TEXT_BYTES,provider(uri)),
                    StandardCharsets.UTF_8
            );
        }
    }

    private static byte[] readBounded(
            InputStream in,
            int maximum,
            String provider
    ) throws IOException {
        byte[] data=in.readNBytes(maximum+1);
        if(data.length>maximum)
            throw new IOException(
                    "Response from "+provider+" exceeded the permitted size."
            );
        return data;
    }

    private static void verifyStatus(int status,URI uri) throws IOException {
        if(status<200||status>=300)
            throw new IOException("HTTP "+status+" from "+provider(uri)+".");
    }

    private static URI validatedHttpsUri(String value) throws IOException {
        final URI uri;
        try{
            uri=URI.create(value);
        }catch(Exception ex){
            throw new IOException("Invalid network endpoint.",ex);
        }

        if(!"https".equalsIgnoreCase(uri.getScheme()))
            throw new IOException("Only HTTPS network endpoints are permitted.");

        if(uri.getHost()==null||uri.getHost().isBlank())
            throw new IOException("Network endpoint is missing a host.");

        return uri;
    }

    private static String cleanHeaderValue(String value) throws IOException {
        String cleaned=value==null?"":value.trim();
        if(cleaned.indexOf('\r')>=0||cleaned.indexOf('\n')>=0)
            throw new IOException("Invalid HTTP header value.");
        if(cleaned.length()>1024)
            throw new IOException("HTTP header value is too long.");
        return cleaned;
    }

    private static String provider(URI uri){
        String host=uri.getHost();
        if(host==null||host.isBlank())return "remote provider";
        return host.toLowerCase(Locale.ROOT);
    }
}
