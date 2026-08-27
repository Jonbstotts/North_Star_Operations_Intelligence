package com.wtm.ingest;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Security policy for Gmail DataPath document ingestion. */
public final class TrustedSenderPolicy {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");

    private TrustedSenderPolicy() {}

    /**
     * Fail closed: an empty allow-list means Gmail document ingestion is disabled,
     * never "accept everyone".
     */
    public static void requireConfigured(String raw) {
        Set<String> senders = parse(raw);
        if (senders.isEmpty()) {
            throw new SecurityException(
                    "Email document import is blocked until at least one trusted sender is configured."
            );
        }
    }

    /** Validate exact mailbox addresses. Gmail search operators/domain wildcards are not accepted. */
    public static Set<String> parse(String raw) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) return out;

        for (String token : raw.split("[,;]")) {
            String email = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
            if (email.isEmpty()) continue;
            if (!EMAIL.matcher(email).matches()) {
                throw new SecurityException(
                        "Invalid trusted sender address: " + email + ". Use complete email addresses only."
                );
            }
            out.add(email);
        }
        return out;
    }
}
