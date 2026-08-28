package com.wtm.ai;

import com.wtm.config.ConfigService;
import com.wtm.ingest.OperationalAnalytics;
import com.wtm.security.AuthorizationService;
import com.wtm.security.Permission;
import com.wtm.util.MiniJson;
import com.wtm.util.SecureFiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Canonical local NorthStar Intelligence service.
 *
 * <p>Operational values are prepared deterministically by Java before any
 * prompt reaches Ollama. The model is synthesis-only: if NorthStar has no
 * operational/document evidence for a question, this service says so rather
 * than asking the model to fill the gap.</p>
 */
public final class NorthStarIntelligenceService {
    public record Answer(String text, List<String> sources) {}
    public record Status(boolean online, String detail) {}
    private record Chunk(Path source, String text, int score) {}

    private static final NorthStarIntelligenceService INSTANCE = new NorthStarIntelligenceService();
    private static final Pattern WORD = Pattern.compile("[^a-z0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP = Set.of(
            "the", "a", "an", "and", "or", "of", "to", "in", "is", "are", "was", "were",
            "what", "when", "where", "who", "how", "why", "for", "on", "with", "this", "that",
            "our", "we", "i", "me", "my", "it", "do", "does", "did", "can", "could", "would"
    );

    private final Path appRoot = ConfigService.appDataDir();
    private final Path aiRoot = appRoot.resolve("ai");
    private final Path knowledgeRoot = aiRoot.resolve("knowledge");
    private final Path settingsFile = aiRoot.resolve("ai.properties");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private volatile String ollamaUrl = "http://127.0.0.1:11434";
    private volatile String model = "llama3.2:3b";
    private volatile boolean useOperationalData = true;

    private NorthStarIntelligenceService() {
        ensureDirs();
        loadSettings();
    }

    public static NorthStarIntelligenceService get() { return INSTANCE; }
    public String ollamaUrl() { return ollamaUrl; }
    public String model() { return model; }
    public boolean useOperationalData() { return useOperationalData; }
    public Path knowledgeRoot() { return knowledgeRoot; }

    public synchronized void saveSettings(String url, String selectedModel, boolean operational) {
        AuthorizationService.require(Permission.AI_ASSISTANT);
        ollamaUrl = normalizeUrl(url);
        model = selectedModel == null || selectedModel.isBlank() ? "llama3.2:3b" : selectedModel.trim();
        useOperationalData = operational;
        ensureDirs();

        Properties properties = new Properties();
        properties.setProperty("ollama.url", ollamaUrl);
        properties.setProperty("ollama.model", model);
        properties.setProperty("operational.data", String.valueOf(useOperationalData));
        try {
            SecureFiles.storePropertiesAtomic(settingsFile, properties, "NorthStar Intelligence local settings");
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save NorthStar Intelligence settings.", ex);
        }
    }

    public Status testConnection() {
        if (!AuthorizationService.allowed(Permission.AI_ASSISTANT)) {
            return new Status(false, "NorthStar Intelligence permission is not granted.");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ollamaUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new Status(false, "Ollama returned HTTP " + response.statusCode());
            }
            Map<String, Object> root = MiniJson.obj(MiniJson.parse(response.body()));
            boolean found = false;
            Object rawModels = root.get("models");
            if (rawModels instanceof List<?>) {
                for (Object item : MiniJson.arr(rawModels)) {
                    if (!(item instanceof Map<?, ?>)) continue;
                    String name = MiniJson.str(MiniJson.obj(item).get("name"));
                    if (model.equals(name) || name.startsWith(model + ":")) {
                        found = true;
                        break;
                    }
                }
            }
            return found
                    ? new Status(true, "Ollama ready • " + model)
                    : new Status(false, "Ollama online • configured model not found: " + model);
        } catch (Exception ex) {
            return new Status(false, "Ollama offline • start Ollama and load " + model);
        }
    }

    public Answer ask(String question) throws Exception {
        AuthorizationService.require(Permission.AI_ASSISTANT);
        String q = question == null ? "" : question.trim();
        if (q.isBlank()) return new Answer("Ask a question first.", List.of());

        String operational = useOperationalData ? OperationalAnalytics.contextFor(q) : "";
        List<Chunk> documents = retrieveKnowledge(q);
        List<String> sources = new ArrayList<>();
        if (!operational.isBlank()) sources.add("NorthStar Data Collection");
        for (Chunk chunk : documents) {
            String source = chunk.source().getFileName().toString();
            if (!sources.contains(source)) sources.add(source);
        }

        if (operational.isBlank() && documents.isEmpty()) {
            return new Answer(
                    "NorthStar could not find verified operational or knowledge-library evidence relevant to that question. " +
                    "I won't substitute an unrelated dataset or invent an operational fact.",
                    List.of()
            );
        }

        StringBuilder evidence = new StringBuilder();
        if (!operational.isBlank()) {
            evidence.append("\n--- VERIFIED OPERATIONAL CONTEXT ---\n").append(operational);
        }
        for (Chunk chunk : documents) {
            evidence.append("\n--- DOCUMENT SOURCE: ")
                    .append(chunk.source().getFileName())
                    .append(" ---\n")
                    .append(chunk.text())
                    .append('\n');
        }

        String employeeRule = AuthorizationService.allowed(Permission.AI_EMPLOYEE_METRICS)
                ? "Employee-level metrics may be used only when explicitly present in verified evidence."
                : "Do not identify or rank employees by performance; employee-level AI metrics permission is not granted.";

        String prompt = "You are NorthStar Intelligence, an evidence-grounded operations analyst.\n" +
                "CURRENT LOCAL DATE: " + LocalDate.now() + ". Never claim another current date.\n" +
                "Answer ONLY from the supplied NORTHSTAR EVIDENCE. Never substitute an unrelated dataset.\n" +
                "Verified Java analytics have priority over document snippets and model inference.\n" +
                "Preserve supplied calculations; do not manufacture causes, dates, values, or missing events.\n" +
                "For current questions, distinguish today's data from latest imported snapshots or completed days.\n" +
                "For unsupported information, state what is missing instead of inventing it.\n" +
                "Cite document facts with [Source: filename] when filenames are supplied.\n" + employeeRule +
                "\n\nUSER QUESTION:\n" + q +
                "\n\nNORTHSTAR EVIDENCE:\n" + evidence;

        Status status = testConnection();
        if (!status.online()) {
            return new Answer(localFallback(operational, documents) +
                    "\n\nLocal AI engine status: " + status.detail(), sources);
        }

        String body = "{\"model\":\"" + escape(model) +
                "\",\"stream\":false,\"prompt\":\"" + escape(prompt) +
                "\",\"options\":{\"temperature\":0.2}}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(ollamaUrl + "/api/generate"))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Ollama returned HTTP " + response.statusCode() + ". " + response.body());
        }
        String generated = MiniJson.str(MiniJson.obj(MiniJson.parse(response.body())).get("response"));
        return new Answer(generated == null || generated.isBlank()
                ? localFallback(operational, documents)
                : generated.trim(), sources);
    }

    public synchronized Path importDocument(Path source) throws IOException {
        AuthorizationService.require(Permission.AI_KNOWLEDGE_ADMIN);
        Objects.requireNonNull(source, "source");
        ensureDirs();
        if (!Files.isRegularFile(source)) throw new IOException("Select a document file.");
        String ext = extension(source);
        if (!Set.of("txt", "md", "csv", "json", "log", "docx").contains(ext)) {
            throw new IOException("Supported knowledge documents: TXT, MD, CSV, JSON, LOG, and DOCX.");
        }
        String safe = source.getFileName().toString().replaceAll("[^A-Za-z0-9._ -]", "_");
        Path target = knowledgeRoot.resolve(safe);
        if (Files.exists(target)) target = knowledgeRoot.resolve(System.currentTimeMillis() + "-" + safe);
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        if (extractText(target).isBlank()) {
            Files.deleteIfExists(target);
            throw new IOException("No readable document text was found.");
        }
        SecureFiles.restrictFile(target);
        fire();
        return target;
    }

    public synchronized void removeDocument(Path file) throws IOException {
        AuthorizationService.require(Permission.AI_KNOWLEDGE_ADMIN);
        if (file == null) return;
        Path normalized = file.toAbsolutePath().normalize();
        Path root = knowledgeRoot.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) throw new SecurityException("Knowledge file is outside the NorthStar library.");
        Files.deleteIfExists(normalized);
        fire();
    }

    public List<Path> knowledgeFiles() {
        ensureDirs();
        try (var stream = Files.list(knowledgeRoot)) {
            return stream.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    public void addLibraryListener(Runnable listener) { if (listener != null) listeners.add(listener); }
    public void removeLibraryListener(Runnable listener) { listeners.remove(listener); }

    private void fire() { listeners.forEach(Runnable::run); }

    private List<Chunk> retrieveKnowledge(String question) {
        List<String> terms = terms(question);
        List<Chunk> result = new ArrayList<>();
        for (Path file : knowledgeFiles()) {
            try {
                String text = extractText(file);
                for (String piece : chunks(text, 1500, 180)) {
                    int score = score(file, piece, terms);
                    if (score > 0) result.add(new Chunk(file, piece, score));
                }
            } catch (Exception ignored) {
            }
        }
        result.sort(Comparator.comparingInt(Chunk::score).reversed());
        return result.stream().limit(8).toList();
    }

    private String extractText(Path file) throws IOException {
        if (Files.size(file) > 8_000_000) throw new IOException("Document too large.");
        return "docx".equals(extension(file)) ? extractDocx(file) : Files.readString(file, StandardCharsets.UTF_8);
    }

    private String extractDocx(Path file) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                if ("word/document.xml".equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8)
                            .replace("</w:p>", "\n")
                            .replaceAll("<[^>]+>", "");
                }
            }
        }
        return "";
    }

    private String localFallback(String operational, List<Chunk> documents) {
        if (!operational.isBlank()) return operational;
        if (!documents.isEmpty()) return documents.get(0).text();
        return "No supported NorthStar evidence was found for that question.";
    }

    private synchronized void loadSettings() {
        try {
            if (!Files.isRegularFile(settingsFile)) return;
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(settingsFile)) { properties.load(input); }
            ollamaUrl = normalizeUrl(properties.getProperty("ollama.url", ollamaUrl));
            String configuredModel = properties.getProperty("ollama.model", model).trim();
            model = configuredModel.isBlank() ? model : configuredModel;
            useOperationalData = Boolean.parseBoolean(properties.getProperty("operational.data", "true"));
            SecureFiles.restrictFile(settingsFile);
        } catch (Exception ignored) {
            ollamaUrl = "http://127.0.0.1:11434";
            model = "llama3.2:3b";
            useOperationalData = true;
        }
    }

    private void ensureDirs() {
        try {
            SecureFiles.ensurePrivateDirectory(aiRoot);
            SecureFiles.ensurePrivateDirectory(knowledgeRoot);
        } catch (Exception ignored) {
        }
    }

    private static String normalizeUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) value = "http://127.0.0.1:11434";
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        URI uri = URI.create(value);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Ollama URL must use HTTP or HTTPS.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new IllegalArgumentException("Ollama URL requires a host.");
        if ("http".equals(scheme) && !("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host))) {
            throw new SecurityException("Plain HTTP Ollama connections are limited to this computer. Use HTTPS for a remote endpoint.");
        }
        return value;
    }

    private static List<String> chunks(String text, int size, int overlap) {
        List<String> result = new ArrayList<>();
        for (int start = 0; start < text.length();) {
            int end = Math.min(text.length(), start + size);
            String piece = text.substring(start, end).trim();
            if (!piece.isBlank()) result.add(piece);
            if (end >= text.length()) break;
            start = Math.max(start + 1, end - overlap);
        }
        return result;
    }

    private static int score(Path file, String text, List<String> terms) {
        if (terms.isEmpty()) return 1;
        String haystack = (file.getFileName() + " " + text).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            int at = 0;
            int count = 0;
            while ((at = haystack.indexOf(term, at)) >= 0 && count < 8) {
                score += 3;
                count++;
                at += term.length();
            }
            if (file.getFileName().toString().toLowerCase(Locale.ROOT).contains(term)) score += 8;
        }
        return score;
    }

    private static List<String> terms(String question) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String term : WORD.split(question.toLowerCase(Locale.ROOT))) {
            if (term.length() > 2 && !STOP.contains(term)) result.add(term);
        }
        return new ArrayList<>(result);
    }

    private static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
