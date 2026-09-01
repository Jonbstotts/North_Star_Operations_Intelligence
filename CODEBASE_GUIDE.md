# North Star Operations Intelligence — Codebase Guide

## Source of truth

- `src/` is the canonical editable source tree.
- `com.wtm.app.NorthStarMainStable` is the only supported packaged entry point.
- Do **not** reintroduce numbered `NorthStarMain18xx` launcher wrappers. They represented incremental compatibility patches and caused old startup/UI behavior to re-enter newer builds.
- Do **not** commit generated runtime JARs into the repository as source-of-truth artifacts. Build output belongs outside the editable source tree and must be regenerated from the current source/manifest.
- `build.sh` rejects numbered legacy launcher wrappers, verifies the canonical manifest entry point, ZIP integrity, and duplicate archive entries.

When repairing an older module, port the behavior into the current lifecycle/core rather than restoring an obsolete polling launcher or delayed injector.

## Foundational repair standard

North Star fixes must repair the owning abstraction rather than layering symptoms on top of existing behavior.

- Find the authoritative owner of state, policy, presentation, and lifecycle before changing code.
- Prefer one reusable policy/service/component over duplicated conditionals in multiple screens.
- Remove obsolete controls, dead branches, compatibility shims, and temporary migration code once their purpose is complete.
- Preserve a single source of truth for persisted settings; loading, validation, application, and saving must use the same model fields.
- Keep rendering logic deterministic and event-driven. Do not add polling, delayed reinjection, or repeated rebuild loops to make a UI appear correct.
- A repair is not complete merely because the visible symptom disappears. Verify startup/restart behavior, settings persistence, runtime refresh behavior, lifecycle disposal, and adjacent feature paths that share the same owner.
- Temporary repair scripts and one-shot workflows are transport mechanisms only. They must not become runtime architecture and must be removed after the canonical source change is committed and validated.
- Comments should explain architectural intent, invariants, lifecycle/security reasoning, or non-obvious constraints. Do not accumulate chronological patch notes or comments that merely restate the code.
- Before producing a release candidate, run the canonical validation from the cleaned source tree after temporary tooling has been removed.

If a requested behavior exposes a broader ownership problem, correct the ownership problem first and implement the feature on top of that clean foundation.

## Startup and UI shell

- `com.wtm.app.NorthStarMainStable` — canonical runtime-service/bootstrap entry point.
- `com.wtm.app.Main` — application splash/login sequencing, configuration load, and workspace launch.
- `com.wtm.ui.OperationsWorkspaceFrame` — primary application shell, navigation, dashboard composition, provider refresh scheduling, and module lifecycle.
- `com.wtm.modular.ui.ModuleUiCoordinatorLifecycle` — event-driven sidebar/module integration. Structural restoration is allowed only at safe workspace boundaries; ordinary component-add events must remain visual-only.
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
- Swing timers: visual animation/tickers only. Do not use repeating Swing timers to discover, rebuild, or restyle module structure.

Never block the EDT on HTTP, CSV parsing, large image decode, disk traversal, or recursive module reinjection.

## Adding a feature

1. Put durable state in an appropriate model/store/service rather than directly in a panel.
2. Put network access behind a provider/service class using `HttpService`.
3. Add permission enforcement to the action/service boundary.
4. Persist ordinary settings through `ConfigService` and secrets through `ApiCredentialService`.
5. Keep UI construction in the `ui` package and marshal background results back to the EDT.
6. Integrate navigation/module behavior through the current event-driven lifecycle, not a new polling injector.
7. Add a short architectural comment only where behavior or security reasoning is not obvious from the code.