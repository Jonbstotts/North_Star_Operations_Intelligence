# North Star Operations Intelligence — Standalone Architecture v1.2

## Runtime

`com.wtm.app.Main` launches the North Star splash/authentication flow and then `OperationsWorkspaceFrame`. North Star has one product identity and one runtime path; there is no ORIVUE/Classic dashboard branch in this codebase.

## Data isolation

North Star reads and writes only `~/.northstar-operations-intelligence`. Product-split migration from the former shared weather/traffic directory has been removed from runtime code.

## Desktop-first performance model

The target is modern desktop hardware on macOS, Windows, or Linux. Provider/network work is kept off the Swing EDT. Map and webhook I/O use Java 21 virtual threads. Visual overlays use a deterministic high-quality animation path rather than hardware-degrading performance profiles.

## Major areas

- operations dashboard and workspace navigation
- weather / traffic / radar / severe-weather monitoring
- information and KPI ticker systems
- operations calendar and managed media
- sports schedules
- Employee Operations, qualifications, attendance, call-ins, performance, and assignments
- optional Twilio / SendGrid integration
- local users / roles / permissions / audit history
- encrypted API/provider credentials
- seasonal and celebration overlays
