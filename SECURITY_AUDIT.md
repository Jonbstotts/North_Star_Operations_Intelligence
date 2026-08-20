# Weather & Traffic Monitor — v2.9.0 Engineering & Security Audit

## Scope

This review covered the complete Java source tree for the standalone desktop application,
including configuration/credential persistence, HTTP access, map/radar/traffic tile loading,
sports/weather providers, Main Showcase media, operations/celebration automation, API usage
tracking, timers/executors, theme/overlay rendering, and Settings-driven dashboard rebuilds.

This is a source-level hardening review and smoke-test pass. It is not a formal third-party
penetration test and cannot guarantee that no future vulnerability exists.

## High-value findings corrected

### 1. API keys could appear in HTTP exception text

Older HTTP errors included the complete request URL. TomTom places its API key in a query string,
and TheSportsDB v1 can place a key in the path. If such an exception was logged or presented during
troubleshooting, the credential could be exposed.

**Correction:** HttpService no longer places request URLs in HTTP failure messages. Errors identify
only the remote host/status.

### 2. Outbound network calls accepted arbitrary URI schemes

The application only needs encrypted public provider endpoints.

**Correction:** all HttpService requests are now HTTPS-only and require a valid host.

### 3. External response bodies had no application-level size ceiling

A bad/misconfigured remote endpoint could return an unexpectedly large response and increase memory
pressure.

**Correction:** text/API responses are capped at 2 MiB and binary/image responses at 12 MiB.
Bodies are streamed and stopped at the configured limit.

### 4. API usage accounting wrote to disk on every request

Traffic/map rendering can create many tile requests. Rewriting api-usage.properties on each request
created unnecessary I/O, particularly undesirable for a Raspberry Pi SD card.

**Correction:** usage writes are throttled to at most once every 30 seconds and receive a final
shutdown flush.

### 5. Settings rebuilds leaked map tile-loader executors

TileMapPanel owns a five-thread loader pool. Rebuilding the dashboard from Settings created a new
map without shutting down the old loader.

**Correction:** DashboardFrame now shuts down the old TileMapPanel before replacing it.

### 6. Map tile memory/disk caches were effectively unbounded

Long-running displays that were heavily panned/zoomed could retain increasing numbers of images.

**Correction:** in-memory tiles are capped, disk cache age/count is maintained, and old cache files
are cleaned at map startup.

### 7. Disk tile filenames used Java String.hashCode()

Different tile keys can theoretically share the same 32-bit Java hash and therefore the same cache
file.

**Correction:** persistent tile cache filenames now use SHA-256 of the internal tile key.

### 8. Configuration writes were non-atomic

A power loss during config.properties or credentials.properties write could leave a partial file.

**Correction:** configuration, credentials, and usage data use temporary-file + atomic replacement
where supported, with safe fallback when atomic moves are unavailable.

### 9. Local credential/configuration permissions were not consistently hardened

Credentials had a permission pass, but directory/config handling was not centralized.

**Correction:** Linux/macOS application-data directory uses owner-only permissions where supported;
config, credentials, and usage files use owner read/write permissions.

### 10. Legacy TomTom key migration did not immediately remove the old plaintext property

The key was migrated to credentials.properties but could remain in an older config.properties until
the next Settings save.

**Correction:** successful migration immediately rewrites the ordinary configuration without the key.

### 11. User-supplied showcase/photos were decoded without a preflight limit

An accidentally huge/corrupt local image could cause excessive allocation during ImageIO decode.

**Correction:** local image files are checked for file size, pixel count, and dimensions before full
decode. The existing EXIF orientation handling remains intact.

### 12. JSON parser accepted malformed/trailing input and had no nesting limit

Provider JSON is external input.

**Correction:** MiniJson now rejects trailing garbage, malformed strings/numbers/escapes and limits
nesting depth to protect the Java call stack.

### 13. Dead live-score UI/service code remained after sports became schedule-only

The legacy SportsScorePanel and live-score service cache/methods were no longer used.

**Correction:** removed unused production code while retaining the current schedule-tracking model.

## Readability / maintainability cleanup

- Expanded dense one-line weather/radar/traffic refresh methods into documented blocks.
- Sanitized routine console errors so secrets, full URLs, and unnecessary local-path details are not logged.
- Added lifecycle comments around map/showcase cleanup.
- Added focused documentation to security-sensitive helpers instead of repeating comments at every call site.
- Limited Main Showcase directory scans to 100 supported media items per rotation.

## Verification performed

- Full Java 21 production-source compilation succeeded.
- HTTPS-only policy smoke test passed.
- Defensive JSON parsing/nesting-limit tests passed.
- Atomic config + separate credential persistence round-trip passed.
- Test verified a TomTom key is absent from ordinary config.properties.
- Oversized local media preflight rejection passed.
- Static source scan was performed for plaintext HTTP endpoints and common secret-bearing HTTP error patterns.

## Residual / deployment considerations

- credentials.properties is protected by OS file permissions but is not encrypted at rest. For a
  future network-hosted/multi-user deployment, use an OS secret store or environment/managed secret.
- A Raspberry Pi installation should run under a dedicated non-admin OS account.
- Keep Raspberry Pi OS and Java 21 security updates current.
- The captive-portal workflow should use an IT-approved login/acceptance process rather than bypassing
  authentication or access controls.
- Physical access to the Pi/TV remains a deployment security consideration.

## v3.0.0 authentication hardening

The v3.0.0 release adds optional local administrator authentication.

- Startup dashboard login can be enabled or disabled from Settings > Security.
- API Providers and API Usage can be independently protected.
- Passwords are stored only as salted PBKDF2-HMAC-SHA256 derived hashes.
- Per-installation salts are generated with SecureRandom.
- Derived hashes are compared using MessageDigest.isEqual.
- Authentication data uses atomic/private local-file persistence.
- Five failed attempts create a process-wide 30-second throttle.
- Protection-setting changes require the existing password after initial setup.
- API tab authorization is session-scoped and is not persisted as an unlocked state.

This remains local application authentication. It does not replace Raspberry Pi OS account security, disk encryption, physical device security, network controls, or centralized enterprise identity management.
