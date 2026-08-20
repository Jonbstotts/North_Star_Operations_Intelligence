# North Star Operations Intelligence — Standalone Architecture

## Runtime

`com.wtm.app.Main` launches `OperationsWorkspaceFrame` directly. There is no dashboard launcher and no alternate Classic runtime.

Removed from the North Star source tree:

- `DashboardFrame`
- `DashboardLauncher`
- Dashboard Experience / Classic-vs-Workspace configuration
- selectable ORIVUE application identity
- bundled ORIVUE image resources

North Star is the only product identity. Holiday themes remain only as animation/effect definitions for seasonal overlays.

## Data isolation

North Star uses `~/.northstar-operations-intelligence`. On the first launch, if that folder does not yet exist and the old shared `~/.weather-traffic-monitor` folder does, the old folder is copied as a migration seed. It is never moved or deleted. After migration North Star writes only to its own folder.

## Major product areas

- modular operations dashboard
- weather / traffic / radar / severe-weather monitoring
- information ticker
- operations KPIs
- operations calendar
- media / showcase
- sports schedules
- Employee Operations
- qualifications and training
- attendance and call-ins
- performance history
- assignment recommendations
- optional Twilio / SendGrid integration
- users / roles / permissions
- API providers and usage tracking
- seasonal overlays and celebration effects
