# North Star Operations Intelligence — Security Audit v1.2.0

This document summarizes the current application-level security controls. It is a source review and hardening pass, not a third-party penetration test.

## Authentication and authorization

- Named local users with role/permission enforcement.
- PBKDF2-HMAC-SHA256 password hashes with per-user random salts.
- New/reset passwords: 600,000 iterations and 12-character minimum.
- Constant-time derived-hash comparison.
- Login throttling after repeated failures.
- Permission checks live in service/action paths as well as the UI.
- Administrative actions are written to an audit log without secret values.

## Credentials

- Provider credentials are separate from normal configuration.
- Credentials are encrypted at rest with AES-256-GCM.
- A random installation key is stored in a separately permission-restricted file.
- Plaintext credential files from older North Star builds migrate automatically and are removed.
- Environment-variable overrides are available for managed deployments.

## Network controls

- Outbound provider requests are HTTPS-only.
- Request timeouts and bounded response sizes are enforced.
- Error messages identify hosts/status without echoing secret-bearing URLs.
- HTTP header values are validated against CR/LF injection.
- Twilio webhooks require a valid `X-Twilio-Signature`.
- The embedded Twilio listener binds to loopback; public TLS should terminate at an approved reverse proxy/tunnel.
- Call-in session state is time-limited and capacity-limited.

## Files and media

- Sensitive/local state uses owner-only POSIX permissions where supported.
- Writes use temp-file + atomic replacement where supported.
- External/local images use size/dimension preflight limits.
- Map caches are bounded and old files are pruned.

## Remaining deployment controls

Use full-disk encryption, OS account protections, current Java/OS security updates, trusted network controls, and enterprise endpoint management where appropriate. For a future centralized deployment, prefer an enterprise secret vault and centralized identity provider over local files/accounts.
