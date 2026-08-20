# North Star Operations Intelligence

**Version 1.0.1**

Standalone operations application. North Star no longer contains the Classic ORIVUE dashboard or a dashboard-experience switch.

## Included
- modular operations workspace
- weather, traffic, radar and severe-weather monitoring
- Main Showcase and managed media
- Operations Calendar
- sports schedules
- configurable Information strip/ticker
- Operations Snapshot KPIs
- Employee Operations
- training/qualifications
- attendance and call-ins
- performance records
- daily assignment recommendations
- optional Twilio/SendGrid notification integration
- users, roles and permissions
- seasonal overlays and celebration effects

## Build
Requires JDK 21.

```bash
./build.sh
```

Output: `NorthStarOperations.jar`

## Local data
`~/.northstar-operations-intelligence`

On first launch, data may be copied from the former shared `~/.weather-traffic-monitor` directory. After that North Star is independent.


## 1.0.1 visual-system note
The application now uses the approved North Star raster mark/wordmark as the canonical identity source. Settings controls also use a platform-independent North Star combo-box delegate so selected values remain vertically centered and readable on macOS, Windows, and Raspberry Pi/Linux.
