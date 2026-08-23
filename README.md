# North Star Operations Intelligence

**Version 1.2.0**

North Star is a standalone desktop operations application built on Java 21 and Swing. The application has one runtime and one product identity; the former shared/Classic application branch is not part of this codebase.

## Included
- modular operations workspace
- weather, traffic, radar and severe-weather monitoring
- Main Showcase and managed media
- Operations Calendar
- sports schedules
- configurable Information strip/ticker
- Operations Snapshot KPIs and CSV imports
- Employee Operations
- training/qualifications
- attendance and call-ins
- performance records
- daily assignment recommendations
- optional Twilio/SendGrid notification integration
- users, roles, permissions and audit history
- encrypted provider credentials
- seasonal overlays and celebration effects

## Runtime target
North Star is optimized for modern desktop-class hardware on macOS, Windows, and Linux. Raspberry Pi performance constraints are no longer a design target.

## Build
Requires JDK 21.

```bash
./build.sh
```

Output: `NorthStarOperations.jar`

## Local data
`~/.northstar-operations-intelligence`

North Star reads and writes only its own application-data directory. Provider credentials are stored separately in encrypted form.

## Engineering documentation
- `CODEBASE_GUIDE.md` — package boundaries, threading model, extension rules, and security boundary.
- `ENGINEERING_AUDIT_V1_2.md` — v1.2 performance/security cleanup and architectural decisions.
- `SECURITY_AUDIT.md` — current application-level security controls and deployment considerations.
