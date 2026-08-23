# North Star Operations Intelligence — Architecture

## Runtime

`com.wtm.app.Main` owns startup, configuration loading, splash/login sequencing, and launches `OperationsWorkspaceFrame`. There is one North Star runtime path and no alternate dashboard implementation.

## Data isolation

North Star reads and writes only `~/.northstar-operations-intelligence`. Runtime migration from the former shared product directory has been removed.

## Primary layers

- `ui` — Swing presentation and interaction.
- `model` — weather, traffic, sports, calendar, KPI and dashboard records/configuration.
- `weather`, `alerts`, `traffic`, `radar`, `sports`, `location` — provider adapters and domain services.
- `employee` — employee system of record.
- `callin` — Twilio call-in receiver and management notifications.
- `importer` — reusable CSV import pipeline for employee and KPI feeds.
- `security` — authentication, permissions, sessions and audit trail.
- `config` — ordinary configuration and encrypted credential persistence.
- `media` — managed application media.
- `net` — centralized HTTPS client.

## Threading

Swing's EDT is reserved for UI work. Network calls and data loading run in background workers/executors. Java 21 virtual threads are used for map-tile and webhook I/O. Visual animation uses Swing timers so rendering updates remain serialized with the UI.

See `CODEBASE_GUIDE.md` and `ENGINEERING_AUDIT_V1_2.md` for detailed extension and security guidance.
