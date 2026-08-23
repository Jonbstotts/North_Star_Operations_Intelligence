# North Star Operations Intelligence — Engineering Audit v1.2.0

## Objective

This pass treats North Star as a desktop-class operations application. Raspberry Pi constraints are no longer part of the design target. The priorities are: responsiveness, security, predictable lifecycle management, readable architecture, and removal of product-split leftovers.

## Architectural decision: keep Swing

Swing remains viable for this application. The workload is primarily dashboards, forms, tables, timers, image/media presentation, and network-backed data. None of those require a framework rewrite. A rewrite would introduce migration risk without a proportional performance or security gain.

The recommended direction is to continue separating domain/service logic from Swing components so a future web or JavaFX client could reuse the same services if needed. The current package boundaries already support that direction.

## Cleanup completed

- Removed the old ORIVUE/shared-folder product-split migration from North Star startup.
- Removed the legacy administrator-authentication migration path and its unused classes.
- Removed two dead UI classes that had no production references.
- Removed obsolete v4/Classic terminology from production comments.
- Removed the old Information auto-scroll compatibility flag.
- Removed the Raspberry-Pi-oriented overlay performance modes and adaptive frame-cost monitor.
- Seasonal overlays now run a deterministic high-quality desktop rendering path.

## Performance improvements

- Map tile downloads now use Java 21 virtual threads instead of a fixed five-thread pool.
- The Twilio webhook listener uses virtual threads for request handling.
- The dashboard refresh scheduler now has capacity for all current provider refresh jobs concurrently.
- Overlay animation uses a fixed ~60 FPS timer and cached static frost rendering rather than dynamically degrading based on measured frame cost.
- Existing bounded caches, image preflight checks, response-size limits, atomic persistence, and dashboard-module cleanup remain in place.

## Security improvements

- API/Twilio/SendGrid credentials are now stored in `credentials.enc` using AES-256-GCM.
- A random per-installation 256-bit key is stored separately as `credentials.key`, with owner-only permissions where supported.
- Existing plaintext `credentials.properties` is automatically migrated to encrypted storage and removed.
- Environment-variable overrides are supported for managed deployments:
  - `NORTHSTAR_WEATHER_API_KEY`
  - `NORTHSTAR_TOMTOM_API_KEY`
  - `NORTHSTAR_SPORTS_API_KEY`
  - `NORTHSTAR_TWILIO_ACCOUNT_SID`
  - `NORTHSTAR_TWILIO_AUTH_TOKEN`
  - `NORTHSTAR_SENDGRID_API_KEY`
- New/reset account passwords use PBKDF2-HMAC-SHA256 with 600,000 iterations and a 12-character minimum. Existing accounts remain compatible because their stored iteration count is retained.
- SessionManager rejects disabled/null accounts.
- The embedded call-in listener binds to loopback rather than all network interfaces. An approved HTTPS reverse proxy/tunnel should terminate external traffic and forward locally.
- Twilio session state expires after 15 minutes and is capped to prevent unbounded in-memory growth.
- The call-in health endpoint returns only `OK` rather than product-identifying text.

## Residual security considerations

The encrypted credential key and ciphertext are both local to the same OS account. This protects against accidental disclosure and casual file inspection, but it cannot protect secrets from an attacker who already controls that OS account. For a centrally hosted or enterprise-managed deployment, move secrets to an OS keychain, enterprise vault, or injected environment variables.

Local application authentication is not a replacement for endpoint security, full-disk encryption, network segmentation, patch management, or enterprise identity controls.

## Verification

The release build is compiled with Java 21. Static scans verify that removed split/Pi performance identifiers are absent from production source, deleted classes have no remaining references, credentials no longer persist as plaintext properties, and the encrypted credential store can round-trip and migrate legacy credential data.
