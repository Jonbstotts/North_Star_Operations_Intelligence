# North Star Operations Intelligence — Codebase Guide

## Startup and UI shell

- `com.wtm.app.Main` — application bootstrap, splash/login sequencing, configuration load, and launch.
- `com.wtm.ui.OperationsWorkspaceFrame` — primary application shell, navigation, dashboard composition, provider refresh scheduling, and module lifecycle.
- `com.wtm.ui.SettingsDialog` — centralized settings model and the embedded settings pages used by sidebar routes.

Swing components must remain responsive: network and filesystem work belongs off the Event Dispatch Thread (EDT); only component mutations return to the EDT through `SwingUtilities.invokeLater`/`SwingWorker.done`.

## Configuration and secrets

- `AppConfig` — in-memory non-secret site/application configuration.
- `ConfigService` — normal configuration persistence.
- `ApiCredentialService` — encrypted provider credential persistence and environment overrides.
- `SecureFiles` — private-directory permissions and atomic file replacement.

Do not add API keys, auth tokens, passwords, or call-in secrets to `config.properties`, logs, exception text, or audit messages.

## Security boundary

- `UserService` — password hashing and local account persistence.
- `SessionManager` — current authenticated account.
- `AuthorizationService` — permission enforcement.
- `AuditService` — security/administrative action history without secret values.

Any new privileged workflow must call `AuthorizationService.require(...)` in the service/action path, not only hide a button in the UI.

## Provider layer

- `HttpService` — HTTPS-only bounded HTTP client.
- `OpenMeteoService`, `NwsAlertService`, `RainViewerService`, `TomTomService`, `TheSportsDbService` — provider adapters.

Provider code should translate remote responses into model records. UI code should not parse provider JSON directly.

## Dashboard/data domains

- `model` — immutable/simple data structures for weather, routes, sports, calendar, and KPI configuration.
- `importer` — reusable CSV import architecture for employee and KPI feeds.
- `employee` — employee system of record, training, attendance/call-ins, performance, and duty qualification.
- `callin` — Twilio webhook lifecycle, validation, recording, and management notifications.
- `media` — managed media library.

## Threading model

- Swing EDT: layout, rendering, input, short state updates only.
- Scheduled refresh executor: provider refresh cadence.
- Java 21 virtual threads: map tile downloads and webhook request handling.
- Swing timers: visual animation/tickers only.

Never block the EDT on HTTP, CSV parsing, large image decode, or disk traversal.

## Adding a feature

1. Put durable state in an appropriate model/store/service rather than directly in a panel.
2. Put network access behind a provider/service class using `HttpService`.
3. Add permission enforcement to the action/service boundary.
4. Persist ordinary settings through `ConfigService` and secrets through `ApiCredentialService`.
5. Keep UI construction in the `ui` package and marshal background results back to the EDT.
6. Add a short architectural comment only where behavior or security reasoning is not obvious from the code.
