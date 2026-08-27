package com.wtm.security;

import com.wtm.config.ConfigService;
import com.wtm.util.SecureFiles;

import java.nio.file.*;

/**
 * Applies owner-only permissions to NorthStar application-data locations that
 * may contain credentials, OAuth tokens or provider authorization material.
 * Safe to call repeatedly; unsupported/non-POSIX platforms retain their native
 * account ACL behavior.
 */
public final class LocalSecurityHardening {
    private LocalSecurityHardening() {}

    public static void install() {
        hardenNow();
    }

    public static void hardenNow() {
        try {
            Path app = ConfigService.appDataDir();
            SecureFiles.ensurePrivateDirectory(app);
            restrictIfPresent(app.resolve("credentials.properties"));
            restrictIfPresent(app.resolve("users.properties"));
            restrictIfPresent(app.resolve("auth.properties"));
            restrictIfPresent(app.resolve("audit.log"));

            Path gmail = app.resolve("credentials");
            SecureFiles.ensurePrivateDirectory(gmail);
            restrictIfPresent(gmail.resolve("gmail-oauth-client.json"));
            restrictIfPresent(gmail.resolve("gmail-token.properties"));
            restrictIfPresent(gmail.resolve("gmail-processed.properties"));

            Path music = Paths.get(System.getProperty("user.home"), ".northstar");
            SecureFiles.ensurePrivateDirectory(music);
            restrictIfPresent(music.resolve("music.properties"));
        } catch (Exception ex) {
            System.err.println("NorthStar local security hardening could not complete: " + ex.getMessage());
        }
    }

    private static void restrictIfPresent(Path file) {
        if (file != null && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            SecureFiles.restrictFile(file);
        }
    }
}
