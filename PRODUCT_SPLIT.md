# North Star Operations Intelligence — Standalone

North Star is now an independent operations application. The Classic ORIVUE DashboardFrame, DashboardLauncher runtime switch, Dashboard Experience setting, and Classic-vs-Workspace handoff have been removed from this source tree.

North Star always launches the modular Operations Workspace. Employee Operations, call-in/Twilio support, training, attendance, performance, assignment recommendations, operations KPIs, information ticker, weather/traffic modules, calendar, media, sports, severe-weather behavior and seasonal overlays remain part of this product.

The interface identity is fixed to North Star. Seasonal themes are used only to drive decorative overlay behavior; they do not switch the product back to ORIVUE.

North Star stores data independently under `~/.northstar-operations-intelligence`. On first launch only, if the former shared `~/.weather-traffic-monitor` directory exists, its contents are copied as a starting point. The legacy folder is never moved or deleted.
