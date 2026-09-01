# NorthStar Foundational Repair Audit — 2026-09-01

This audit revalidated recent dashboard, severe-weather, movement, persistence, and company-glyph repairs before the next release-candidate build.

## Verified company glyph source

The exact supplied NorthStar dashboard glyph rows restored in commit `64be672061019b8f06a9b2be2e0075225bcc9da2` remain the canonical resources. Follow-up geometry and coverage gates in `ae7d38250f3453d14775542fb1053a7be59001a7` and `d47cc9347d340b6152139649cee920ab93680696` require 648×72 nine-cell atlas rows and exercise all 36 supplied company glyphs through Java ImageIO/rendering.

## Dashboard grid and persistence

The dashboard now uses current 24-column geometry as its source default and records `_gridVersion=2`. `ConfigService` treats a persisted dashboard layout as one map rather than mixing saved entries with current defaults. `DashboardGridPanel` classifies an entire unversioned map before migration, preventing partial re-scaling of layouts already using 24-column coordinates. The retired pre-grid `mapWidthPercent` state and unused `FixedRatioLayout` implementation were removed.

## Information and Operations Snapshot movement

Information and Operations Snapshot movement retain independent STATIC, PAGED, and TICKER configuration. Dashboard lifecycle release/rebuild boundaries stop both movement timer systems so navigation cannot leave orphaned Swing timers active.

## Severe-weather ownership

`WeatherAlertPolicy` is the canonical owner for alert priority, severe qualification, concise event naming, and presentation level. `SevereWeatherRefreshPolicy` is the canonical owner for normal versus rapid weather/NWS/radar polling cadence. Manual Live Severe Weather Mode and qualifying automatic alerts feed the same severe-weather presentation state for map priority, showcase behavior, overlays, badge state, ticker entries, and alert details.

## Permanent regression gates

`build.sh` now includes the permanent weather-alert policy, dashboard-grid migration, and configuration round-trip smoke tests in addition to the existing strict Java 21 compile, 25-theme smoke test, 36-company-glyph smoke test, packaging checks, branding checks, and canonical architecture guards.

The source hardening was committed as `264a7216e132573a39dd1158e276178d0b92f526` (`Harden alert, grid, and dashboard repair foundations`). Temporary audit/repair transport workflows and scripts were removed from the canonical tree after successful validation.
