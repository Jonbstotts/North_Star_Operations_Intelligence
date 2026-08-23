# v1.1.13 — Appearance & Workspace display overhaul

- Renamed General to Appearance and grouped dashboard chrome/theme controls into consistent rounded cards.
- Activated dashboard header text and continuous message ticker in the modern Operations Workspace.
- Reworked Workspace Setup language and grouping for clearer first-time configuration.
- Added independent Operations Snapshot display controls: visible count, Static, Paged Rotation, Continuous Ticker, page interval, and ticker speed.
- Persisted Operations Snapshot movement settings alongside Information Row settings.
- Added real paged/ticker rendering for Operations Snapshot KPI cards.
- Preserved Information Row movement controls and map/information layout behavior.

# North Star Operations Intelligence Changelog

## 1.0.1 — Settings control geometry and canonical North Star artwork
- Replaced the remaining Java2D approximation of the North Star symbol with the approved raster identity extracted directly from the supplied North Star reference artwork.
- Added canonical bundled brand assets for the exact metallic monitoring ring, three-point path, focus star, blue horizon, NORTH STAR wordmark, and OPERATIONS INTELLIGENCE tagline.
- Splash, login/setup lockups, workspace header symbol, Settings header symbol, window icon, and application icon now all resolve through the same North Star brand service.
- Vertical lockups use the approved primary-logo artwork directly instead of reconstructing the wordmark with system fonts.
- Rebuilt the platform-independent combo-box delegate with a true 42 px interior, full-height selected-value renderer, padded popup rows, and a dedicated arrow region.
- Fixed the thin/clipped selected text visible in General, Operations Calendar, Main Showcase, Data & Refresh, API Providers, Information Blocks, Employee Operations, Call-In Integration, and other settings pages on macOS.
- ThemeStyler now installs the North Star combo delegate recursively, so future settings pages inherit the corrected control automatically.
- Increased shared Settings form padding and row spacing for a cleaner 1080p/4K presentation.
- Increased Employee Operations form spacing and label width, including Call-In Integration and attendance/training forms.
- Standardized text fields, password fields, spinners, and combo boxes to consistent control heights.
- Renamed residual runtime thread/placeholder identifiers from ORIVUE to North Star naming in the standalone codebase.

## 1.0.0
- Fullscreen dark-mode workplace dashboard
- Vance/Tuscaloosa/Birmingham default weather strip
- Cached road map with pan and zoom
- RainViewer weather-radar overlay
- NWS severe-weather alerts and GeoJSON map polygons
- TomTom live traffic-flow overlay when an API key is supplied
- TomTom traffic-aware route travel times for three configurable routes
- Six configurable information blocks
- Rotating image media block
- Optional facility title and scrolling announcement ticker
- Settings-only application exit
- Local configuration/cache/media storage
- Resilient refresh behavior that retains last successful data
- Java 21 dependency-free build and launcher scripts for macOS/Linux/Windows

## 1.0.1
- Fixed RainViewer radar tiles displaying “Zoom Level Not Supported” when the base map zoom exceeded RainViewer's maximum z=7.
- Higher map zooms now crop/upscale the correct z=7 radar parent tile while preserving road-map detail.

## 1.0.2
- Increased separation between the three top forecast cards and gave forecast cards a distinct secondary surface in light and dark themes.
- Switched RainViewer radar requests from 256px to 512px tiles for higher visual resolution.
- Added bilinear radar scaling above RainViewer zoom 7 to reduce blocky/pixelated enlargement while preserving detailed road-map zoom.

## 1.1.0
- Reworked the dashboard visual system to match the polished operations-display concept.
- Standardized 14px spacing between header, forecasts, map, widgets, and ticker.
- Added consistent rounded outlines to cards in both light and dark mode.
- Added built-in vector weather icons to location forecasts and current-weather widgets.
- Added built-in route, severe-alert, wind, forecast, media, and system-status icons.
- Improved route cards with traffic-severity status coloring.
- Refined typography hierarchy and card padding for large TV readability.
- Adjusted map/widget split so information blocks have a more balanced, uniform footprint.
- Refined light and dark palettes while preserving existing API, radar, traffic, settings, and caching behavior.

## 1.2.0
- Added an unlimited pinned-location editor in Settings.
- Added Hoover and Trussville to the fresh-install Vance configuration.
- Pinned locations automatically appear on the map and become selectable weather widgets.
- Replaced the fixed three-route settings form with an unlimited route table.
- Added a one-click “Route from selected pin” workflow.
- Dashboard widget choices are now generated from current pinned locations and routes.
- Added selectable 6, 8, 10, or 12 information blocks beside the map.
- Ten/twelve-block layouts use a three-column adaptive grid for large TV displays.
- Forecast cards wrap after five columns so large location lists remain readable.
- Existing configuration files migrate forward; current TomTom/API settings remain external to the JAR.

## 1.2.1
- Removed the interactive JSplitPane divider between the map and information blocks.
- Eliminated the small divider/resize control that could accidentally shrink the map or enlarge the cards.
- Replaced the split pane with a responsive fixed 63/37 GridBagLayout.
- The dashboard still scales with fullscreen/window size, but the map-to-card ratio can no longer be manually dragged.

## 1.2.2
- Fixed the map shrinking after the dashboard completed its Swing layout pass.
- Replaced GridBagLayout for the map/card regions with a deterministic FixedRatioLayout.
- The map now permanently receives 63% of available dashboard width and cards receive 37%.
- Card preferred/minimum sizes can no longer force the map narrower after startup.
- Removed all interactive or implicit map/card resizing behavior while preserving normal fullscreen/window scaling.

## 1.3.0
- Added controlled map/information resizing in Settings > Dashboard Blocks.
- Added a 55%–75% map-width slider with a locked complementary information-panel percentage.
- Added one-click Information Focused (55/45), Balanced (63/37), and Map Focused (70/30) presets.
- The chosen ratio is saved in the site configuration and restored on every launch.
- Save & Apply immediately rebuilds the dashboard at the selected ratio.
- Normal dashboard operation remains non-draggable, preventing accidental TV-layout changes.

## 1.3.1
- Added quick refresh-rate controls under Settings > Data & APIs.
- Route/traffic refresh can now be set to 2, 5, 10, 15, 20, or 30 minutes.
- Weather refresh can be set to 5, 10, 15, 20, 30, or 60 minutes.
- Radar refresh can be set to 2, 5, 10, or 15 minutes.
- NWS alert refresh can be set to 1, 2, 5, 10, or 15 minutes.
- Save & Apply now cancels and recreates background scheduler jobs immediately.
- Refresh-rate changes no longer require an application restart.
- Existing custom interval values are preserved and shown even if they are not one of the standard presets.

## 1.3.2
- Added persistent Live Severe Weather Mode under Settings > Data & APIs.
- Live mode refreshes NWS alerts every 1 minute.
- Live mode refreshes radar every 2 minutes.
- Live mode refreshes current weather every 2 minutes.
- TomTom traffic/routing remains on the normal user-selected interval to protect API usage.
- Turning Live Severe Weather Mode off restores the normal weather/radar/alert refresh settings.
- Save & Apply switches between live and normal scheduler intervals immediately without restarting.

## 1.4.0
- Added Automatic Severe Weather Mode.
- Added a separate checkbox to enable/disable automatic triggering.
- Added an Auto Return checkbox, enabled by default, to return to normal refresh rates after qualifying severe alerts clear.
- Automatic mode triggers for Tornado Warning/Watch, Tornado Emergency, Severe Thunderstorm Warning/Watch, Flash Flood Warning, Extreme Wind Warning, and NWS alerts classified as Extreme.
- Automatic live polling uses 1-minute NWS alerts plus 2-minute radar and current-weather checks.
- TomTom traffic/routing remains on its normal selected interval.
- Manual Live Severe Weather Mode remains independent and always overrides normal polling while selected.
- System Status now displays NORMAL, MANUAL LIVE, or AUTO LIVE and shows the triggering alert when available.

## 1.5.0
- Converted the large map region into a configurable Main Showcase.
- Added Settings > Main Showcase.
- Main Showcase can remain map-only or cycle between the live map and announcement images.
- Added configurable 10-second through 5-minute showcase intervals.
- Main Showcase uses PNG/JPG/JPEG/GIF files from the configured media folder.
- Added Severe Weather Map Priority, enabled by default.
- AUTO LIVE severe-weather monitoring immediately forces the Main Showcase back to the map and pauses media rotation.
- Media rotation resumes automatically after the automatic severe-weather state clears.
- Severe Weather Map Priority can be disabled independently for troubleshooting/testing.
- The smaller Media dashboard block remains independent from Main Showcase rotation.

## 1.5.1
- Removed media filenames from the Main Showcase.
- Announcement images now use the full Main Showcase region without a filename/caption bar.
- AUTO LIVE severe-weather mode now takes priority over the bottom ticker.
- AUTO LIVE ticker text identifies the triggering NWS alert when available.
- MANUAL LIVE mode also displays a distinct live-weather ticker status.
- When live severe-weather mode clears, the normal configured ticker message returns automatically.
- Ticker status refreshes immediately when severe-weather state changes.

## 1.6.0
- Added Settings > API Providers for centralized provider and credential management.
- Added Open-Meteo Free and Open-Meteo Customer weather-provider choices.
- Open-Meteo Customer mode uses customer-api.open-meteo.com and an API key entered in Settings.
- Added configurable NWS User-Agent identification.
- Listed installed alert, radar, traffic/routing providers in Settings for future adapter expansion.
- Moved TomTom and weather API secrets into a separate credentials.properties file.
- Added automatic migration of an existing TomTom key from older config.properties files.
- On POSIX systems, credentials.properties is restricted to owner read/write where supported.
- System Status now identifies the configured weather and traffic providers/credential state.

## 1.7.0
- Added a dedicated Sports configuration tab.
- Sports selections behave like routes: each configured selection automatically appears under Dashboard Blocks as a Sports Score option.
- Added TheSportsDB as the first sports provider, with provider/key controls under API Providers.
- Added editable Alabama Football and Tennessee Football examples using TheSportsDB NCAA Division 1 Football IDs.
- Sports score cards display home/away teams, scores when available, game status, kickoff time, and team artwork/logos.
- Added configurable sports refresh intervals from 2 to 60 minutes.
- Free TheSportsDB mode supports team artwork, upcoming events, and recent/final results.
- Premium TheSportsDB mode can use the v2 live-score endpoint when a premium API key is supplied.
- Sports credentials are stored with the application's other API credentials.
- Sports service and normalized models are separated from the UI for future provider adapters.

## 1.8.0
- Added Find Team to Settings > Sports.
- Team search is provider-backed and supports any sport/team returned by the configured provider.
- Search results display team name, league, sport, country, Team ID, League ID, and provider.
- Added team-logo preview inside the Find Team result dialog.
- Use This Team automatically fills Sport, League ID, Team ID, Team Name, and enables logos.
- Find Team can populate an existing selected sports row or create a new sports selection automatically.
- Newly configured teams continue to appear automatically under Dashboard Blocks as Sports Score choices.
- Added provider-neutral TeamSearchResult model so future sports providers can implement the same search workflow.
- TheSportsDB v2 premium team search is supported; the UI clearly reports current free-v1 general-search restrictions instead of silently failing.

## 1.9.0
- Added Find Location search to Settings > Pinned Locations.
- Added Find Primary Location to populate the primary facility/map-center fields.
- Added Find Destination to Settings > Routes.
- Location search uses Open-Meteo's keyless Geocoding API and ranked GeoNames place results.
- Search results show location, state/region, country, latitude, longitude, timezone, and population.
- Choosing a result automatically fills latitude/longitude and descriptive location fields.
- Find Location can populate an existing selected row or create a new pinned location.
- Find Destination can populate an existing route or create a new route directly.
- Manual coordinate entry remains available for exact sites or places not represented in the geocoder.
- Added provider-neutral LocationSearchResult so another geocoder can be introduced later without redesigning the UI.

## 2.0.0
- Replaced the binary Light/Dark option with a full application theme system.
- Added ten built-in themes: Dark, Light, Graphite/Silver, Operations Blue, Midnight Blue, Slate, Emerald, Amber/Night, High Contrast, and Warm Neutral.
- Added a live theme palette preview under Settings > General.
- Theme selection controls dashboard backgrounds, cards, secondary surfaces, borders, text, muted text, and accent colors.
- Dark-family presets automatically use the dark map/traffic presentation; light-family presets automatically use the light map presentation.
- Existing installations migrate automatically: prior Dark Mode becomes Dark and prior Light Mode becomes Light.
- Theme IDs are persisted in site configuration so each facility can maintain its own visual identity.
- Centralized palette architecture makes additional themes straightforward to add later without changing individual widgets.

## 2.1.0
- Added Settings > API Usage.
- Added installation-local request accounting through the centralized HttpService.
- Tracks TomTom tile requests separately from TomTom non-tile/routing requests.
- Tracks Open-Meteo, NWS, RainViewer, and TheSportsDB requests.
- Persists counters across application restarts in api-usage.properties.
- Displays current allowance period, known limit, percentage used, status, and provider notes.
- Adds OK / WATCH / WARNING / CRITICAL threshold states at <60%, 60%, 80%, and 95%.
- Clearly labels locally tracked usage separately from provider account-wide usage.
- Added Reset Local Counters for troubleshooting without altering provider-side usage.
- Uses current TomTom daily allowance model (50,000 tiles / 2,500 non-tile) rather than an obsolete monthly allowance.

## 2.1.1
- Fixed API Usage table text blending into the background on dark-family themes.
- Made the API Usage JTable fully theme-aware.
- Applied theme colors to table body, headers, viewport, grid lines, borders, buttons, summary text, and selections.
- Added automatic contrasting selection text for bright/dark accent colors.
- Preserved status coloring for INFO, WATCH, WARNING, and CRITICAL rows without sacrificing readability.
- No API counting or quota logic changed in this patch.

## 2.2.0
- Extended application themes across the entire Settings interface.
- Added recursive ThemeStyler support for tabs, panels, labels, inputs, password fields, text areas, checkboxes, combo boxes, buttons, sliders, tables, headers, scroll panes, viewports, and separators.
- Settings now previews the selected theme live across the full dialog before Save & Apply.
- Added universal theme support to Find Location and Find Team dialogs.
- API Usage now participates in the same universal settings-theme infrastructure while preserving usage-status colors.
- Added Holiday • Christmas.
- Added Holiday • Halloween.
- Added Holiday • Thanksgiving.
- Added Holiday • Independence Day.
- Added Holiday • Valentine’s Day.
- Added Holiday • St. Patrick’s Day.
- Added Seasonal • Winter Frost.
- Existing operational themes remain available.

## 2.2.1
- Fixed misaligned/clipped dropdown controls introduced by universal theming on macOS.
- Replaced platform-native JComboBox painting in themed Settings screens with a consistent BasicComboBoxUI implementation.
- Removed the white native interior strip visible beneath dark-themed combo-box values.
- Standardized dropdown height, text padding, vertical centering, popup-row height, arrow-button width, borders, and selection colors.
- Applied the fix globally to Theme, Dashboard Blocks, Main Showcase, API Providers, Data & Refresh, and all future Settings combo boxes.
- Standardized themed text-field height and padding for better row alignment.
- Updated GridBag form rows to center controls consistently across macOS, Windows, and Raspberry Pi OS.
- No dashboard functionality, API logic, or saved configuration format changed.

## 2.3.0
- Added Settings > Team Celebrations.
- Added local birthday and work-anniversary records with optional employee photos.
- Birthday records use month/day only; work-anniversary records retain hire year so completed years can be calculated automatically.
- Today's matching birthday/anniversary records generate temporary Main Showcase slides automatically.
- Celebration slides disappear automatically when the date no longer matches.
- Long-running displays refresh date-driven celebration content automatically across midnight without requiring an application restart.
- Added optional initials-based celebration artwork when no employee photo is supplied.
- Added photo import that copies selected employee images into the local celebrations-media application-data directory.
- Added one-time confetti animation when a celebration slide first appears during an application session.
- Confetti can be disabled per team member.
- Added optional application-wide theme overlay effects.
- Christmas and Winter Frost use snowfall.
- Halloween uses subtle drifting spooky particles.
- Thanksgiving uses falling autumn leaves.
- Independence Day uses red/blue spark particles.
- Valentine's Day uses floating hearts.
- St. Patrick's Day uses shamrock particles.
- Added Low / Medium / High overlay intensity.
- Automatic severe-weather map priority suppresses all decorative overlays and celebration effects immediately.
- Decorative effects resume after automatic severe-weather priority clears.
- Celebration names, dates, and photo paths remain local site configuration and are not compiled into the source code.

## 2.3.1
- Reworked holiday effects so each theme has its own distinct animation system instead of generic recolored particles.
- Halloween now uses layered rolling fog banks with translucent haze and horizontal movement across the display.
- Independence Day now uses launch-and-burst fireworks with rising rocket trails, radial bursts, secondary colors, spark trails, fade-out, and gravity.
- Christmas and Winter Frost now use individually drawn six-arm snowflakes instead of falling ovals.
- Snowflakes vary in size, depth, sway, rotation, fall speed, and opacity for a more natural snowfall effect.
- Christmas/Winter Frost now add a subtle frosted-glass edge treatment and crystalline frost detail around the display perimeter.
- Overlay intensity continues to control the density/frequency of snow, fog layers, and fireworks.
- Celebration confetti remains unchanged and independent from holiday theme effects.
- Automatic severe-weather priority still suppresses every decorative effect immediately.

## 2.3.2
- Improved Halloween fog with much wider overlapping fog banks, smoother low-opacity gradients, depth-based movement, slow turbulence, and thin wispy layers.
- Removed the remaining cloud/blob appearance from the Halloween effect in favor of a continuous rolling-mist presentation.
- Added Halloween perimeter string lights with alternating orange and purple bulbs.
- Halloween lights include soft glow halos, visible sockets/bulbs, and a gentle asynchronous twinkle.
- Expanded Thanksgiving leaves from one generic shape to three distinct silhouettes: maple-inspired, oak-inspired, and pointed autumn leaves.
- Added a broader Thanksgiving autumn palette with orange, amber, rust, brown, and muted golden variations.
- Thanksgiving leaves now vary more in size, tumble speed, lateral drift, and sway.
- Existing Christmas snow/frost, Independence Day fireworks, celebration confetti, and severe-weather suppression behavior remain unchanged.

## 2.4.0
- Added optional automatic holiday/seasonal theme switching under Settings > General.
- Automatic holiday switching preserves the saved manual theme as the fallback outside holiday windows.
- Automatic theme windows include January Winter Frost, Valentine's Day lead-in, St. Patrick's Day lead-in, Independence Day week, Halloween season, U.S. Thanksgiving week, and the December Christmas season.
- Long-running displays re-evaluate the effective holiday theme automatically, including across midnight, without requiring an application restart.
- Added Christmas perimeter string lights with red, green, and warm-white bulbs, soft glow, green sockets/wire, and asynchronous twinkle.
- Christmas lights complement the existing snowflake and frost/crystal overlay.
- Further improved Halloween fog with more/larger depth layers, broader fog banks, additional full-screen ground haze, long continuous wispy streams, lower opacity, parallax movement, and softer transitions between banks.
- Retained Halloween orange/purple perimeter lights.
- Upgraded Valentine's Day overlay with layered, gradient-filled heart silhouettes, outer glow, glossy highlights, floating petals, varied pink/red tones, slower drift, and gentle sway.
- Upgraded St. Patrick's Day overlay with shaded dimensional shamrocks, green glow, detailed stems/highlights, and independent gold sparkle/glint particles.
- Existing Thanksgiving multi-shape leaves, Independence Day fireworks, Christmas snow/frost, celebration confetti, and severe-weather suppression remain intact.
- Automatic severe-weather map priority continues to suppress every decorative holiday/celebration effect immediately.

## 2.4.1
- Fixed sideways/rotated celebration photos from phones and cameras.
- Added orientation-aware JPEG loading with direct support for EXIF Orientation tag 0x0112.
- Supports all eight standard EXIF orientation states, including 90/180/270-degree rotation and mirrored/transposed variants.
- Celebration photos are normalized before scaling into birthday/work-anniversary slides.
- Applied the same orientation-aware loader to Main Showcase announcement JPG/JPEG images so phone-originated media displays correctly there as well.
- Upgraded celebration-photo scaling interpolation from bilinear to bicubic/high-quality rendering.
- No celebration scheduling, overlay, theme, severe-weather, API, or configuration behavior changed.

## 2.4.2
- Reworked celebration confetti from a fixed-duration effect into a finite physics-driven shower.
- Confetti now triggers every time an enabled birthday/work-anniversary slide rotates into view.
- Each trigger releases one complete batch; particles are no longer replenished on a timer.
- Every confetti piece remains visible until it naturally falls beyond the display boundary.
- Confetti continues across slideshow transitions, including when the celebration card advances to the map or another announcement.
- Holiday theme overlays continue independently while celebration confetti is falling.
- Seasonal overlay maintenance no longer clears active celebration confetti.
- Confetti spawn positions are staggered above the display for a more natural cascading entrance.
- Severe-weather priority remains the only runtime condition that immediately clears/suppresses celebration confetti.

## 2.4.3
- Fixed phantom celebration confetti that could appear after opening/saving Settings.
- Root cause: dashboard rebuilds replaced the visible Main Showcase but did not stop the old showcase's Swing rotation timer.
- Old invisible showcase timers could continue advancing and later trigger their hidden celebration cards, causing confetti while no celebration slide was visible.
- Added MainShowcasePanel.disposeShowcase() to stop its rotation timer and disconnect the celebration callback.
- DashboardFrame now disposes the existing Main Showcase before every Settings/theme/layout rebuild.
- This also prevents multiple hidden slideshow timers from accumulating after repeated Settings changes.
- Celebration confetti still triggers normally whenever the currently visible celebration slide rotates into view.
- Removed the drawn crystalline line strokes from Christmas/Winter Frost.
- Retained the smooth frosted-edge gradient, snowflakes, and Christmas perimeter lights.

## 2.5.0
- Added Settings > General > Overlay Performance with Automatic, High Quality, Balanced, and Performance modes.
- Added frame-budget-aware adaptive overlay rendering.
- Automatic mode measures overlay paint cost with an exponential moving average and dynamically adjusts ambient density and animation cadence.
- Automatic mode targets approximately 30 FPS under light load, 25 FPS under moderate load, and 20 FPS when overlay rendering becomes expensive.
- Celebration confetti retains priority and is never removed by adaptive ambient-particle trimming.
- When confetti is active, ambient holiday density is temporarily reduced to preserve smooth celebration motion.
- Fireworks retain priority while their maximum simultaneous count is hardware-profile aware.
- Added a hard ambient-particle budget that immediately trims seasonal decoration when adaptive load reduction is needed.
- Halloween fog updates at a lower simulation cadence in Balanced/Performance/heavy Automatic modes while remaining continuously painted.
- Simplified snowflake branch detail only under heavy Automatic load or Performance mode.
- Cached the static Christmas/Winter Frost edge-gradient layer so its gradients are not rebuilt every animation frame.
- Overlay rendering switches from quality-first Java2D hints to speed-first hints under constrained modes/heavy Automatic load.
- Overlay timer cadence is profile-aware: High Quality ~30 FPS, Balanced ~25 FPS, Performance ~20 FPS, Automatic adaptive.
- Idle overlay frames no longer request unnecessary repaints when no decorative animation is active.
- Existing finite celebration-confetti behavior, holiday visuals, severe-weather suppression, and slideshow logic remain unchanged.

## 2.5.1
- Made the Settings window responsive to the current monitor's usable work area.
- Settings now opens wider on larger displays so the full category navigation row is visible without manual resizing.
- Added scrollable JTabbedPane navigation so future settings categories remain reachable on smaller displays instead of being clipped off-screen.
- Initial settings width now considers both the monitor size and the preferred width of the current tab strip.
- Settings height is also capped to the monitor's usable work area so the dialog remains practical on Raspberry Pi/TV and laptop displays.
- Exit Application, Cancel, and Save & Apply remain fixed at the bottom of the dialog.
- Redesigned the St. Patrick's Day shamrock overlay with three heart-shaped clover leaflets instead of circular lobes.
- Added richer emerald gradients, leaflet outlines/veins, center depth, a curved tapered stem, subtle glow, and a small highlight for a more dimensional shamrock.
- Existing gold glint particles, adaptive overlay performance, and severe-weather suppression remain unchanged.

## 2.6.0
- Changed configurable Sports dashboard blocks from live/recent score tracking to upcoming schedule tracking.
- Sports blocks now request the configured team's upcoming schedule and display up to the next three games.
- The nearest upcoming game is emphasized with date/time, home/away context, opponent, league, and configured-team logo when available.
- Two additional future games are displayed as compact schedule rows when provider data is available.
- Dashboard sports titles now use SCHEDULE rather than SPORTS SCORE.
- Dashboard Block customization now labels configured sports choices as Upcoming Schedule.
- Removed premium-live-score polling from normal dashboard refreshes, substantially reducing unnecessary sports API traffic during the work week.
- Premium provider access remains available for enhanced team search where supported.
- Sports refresh presets are now 15, 30, 60, 120, and 240 minutes, with 30–60 minutes recommended.
- Existing installations with old sports refresh values below 15 minutes automatically migrate to a 30-minute schedule refresh.
- Existing SportsConfig records and SPORTS_n dashboard widget IDs remain compatible; users do not need to recreate configured teams or blocks.
- Legacy SportsScorePanel remains in source for compatibility but is no longer used by dashboard blocks.

## 2.6.1
- Fixed sports schedule text using the platform-default black foreground on dark and holiday themes.
- SportsSchedulePanel now applies Theme.text() / Theme.muted() directly when asynchronously generated schedule labels are created.
- Loading, no-schedule, primary matchup, and secondary upcoming-game rows now remain readable across every application theme.
- Redesigned the Wind & Gusts dashboard icon with three clean airflow ribbons and rounded curls for improved readability at TV distance.
- The new wind symbol removes the intersecting/stacked-arc appearance of the previous icon while preserving theme accent coloring.

## 2.6.2
- Refined the Wind & Gusts icon to use the familiar three-line meteorological wind-gust silhouette.
- Added smooth upper, center, and lower curls modeled after conventional weather wind symbols.
- Preserved vector rendering, rounded strokes, scaling, and active-theme accent coloring.

## 2.7.0
- Added Employee of the Month recognition to Settings > Team Celebrations.
- Preserved the one-row-per-team-member model: Birthday, Anniversary, and Employee of the Month are independent recognition options on the same employee record.
- Added an Employee of Month checkbox column to the Team Celebrations table.
- Employee of the Month is single-select: checking one team member automatically clears the selection from every other row.
- The selected recipient is stamped with the current month and year when Save & Apply is used.
- Employee of the Month recognition automatically expires when the calendar moves into a different month.
- Added a current-recipient status line showing the active month/year and selected employee.
- Existing birthday opt-out and anniversary opt-out behavior remains independent.
- Added a dedicated Employee of the Month Main Showcase card with month/year, employee photo or initials, trophy/star artwork, employee name, and congratulatory message.
- Employee of the Month remains visible throughout its assigned month and is kept separate from same-day birthday/anniversary cards.
- Employee of the Month participates in the existing per-employee Confetti setting and triggers the finite confetti shower whenever its slide rotates into view.
- Existing celebration configuration files load safely; older employee rows simply begin with no Employee of the Month assignment.

## 2.8.0
- Added a new Settings > Operations Calendar module.
- Added three reusable operating-status types: Full Closure, Limited Service, and Modified Hours.
- Operations records support both one-day events and multi-day date ranges.
- Added configurable normal operating hours and normal operating weekdays for each installation.
- Added a site-wide default announcement lead time plus optional per-event Lead Days overrides.
- Full Closure events automatically ignore work-hour fields.
- Limited Service and Modified Hours require validated start/end work hours.
- Time entry accepts common formats such as 7:30 AM, 11:00 AM, and 16:00.
- Operations Calendar announcements are generated dynamically and require no uploaded announcement image.
- Upcoming events automatically enter the Main Showcase when their announcement window begins.
- Operations slides automatically disappear after the final event date; no manual cleanup is required.
- Connected/adjacent operations dates are automatically grouped into one announcement slide.
- Example supported grouping: Thanksgiving Full Closure followed by Friday Limited Service becomes one slide.
- Multi-day Modified Hours remain in the slideshow throughout the entire configured date range and disappear after the range ends.
- Generated slide wording changes automatically between UPCOMING, OPERATIONS SCHEDULE IN EFFECT, and FINAL DAY.
- Added visual status treatment for Full Closure, Limited Service, and Modified Hours.
- Modified-hours announcements compare temporary hours against the site's normal operating schedule and can call out earlier starts or earlier/later endings.
- Generated announcements automatically calculate the next normal operating day after a grouped event period.
- Return-to-normal calculation respects configured normal weekdays and skips dates covered by other enabled operations events.
- Severe-weather Main Showcase priority remains unchanged and still takes precedence over operations announcements.
- Existing media, sports, Team Celebrations, Employee of the Month, themes, overlays, and API settings remain compatible.

## 2.8.1
- Multi-day Operations Calendar entries now render as one visual block per calendar day.
- Each block's large colored left badge prominently displays the weekday plus the operation type.
- A Monday-Friday Modified Hours range therefore displays Monday, Tuesday, Wednesday, Thursday, and Friday as separate blocks without duplicate calendar records.
- Past daily blocks are automatically removed as the date range progresses; the current day and future days remain visible.
- Single-day closures, Limited Service entries, and Modified Hours entries retain the same overall announcement format.
- The announcement header continues to show the full configured date range and upcoming/in-effect status.

## 2.8.2
- Fixed Dashboard Blocks dropdowns reverting to native/default white combo-box styling.
- Root cause: Dashboard Block selectors are dynamically rebuilt after Settings theme styling has already occurred.
- Every dynamically-created Dashboard Block JComboBox now immediately passes through the same ThemeStyler used by the rest of Settings.
- Rebuilt Block labels and selectors are re-themed as a complete container after pinned-location, route, sports, or block-count changes.
- Live theme previews now explicitly re-style the Dashboard Blocks container as well.
- Dashboard Block selectors now match General, Data & Refresh, API Providers, and other Settings dropdowns across Dark, Light, holiday, and future themes.

## 2.9.0 — Full engineering/security audit
- Performed a complete source-tree audit focused on 24/7 reliability, Raspberry Pi endurance, credential safety, network input handling, resource lifecycle, cache growth, and maintainability.
- Hardened HttpService to permit HTTPS endpoints only.
- Removed full request URLs from HTTP exception messages to prevent API-key disclosure through logs.
- Added bounded streaming response reads: 2 MiB for API/text responses and 12 MiB for binary images/tiles.
- Added CR/LF and length validation for custom HTTP header values.
- Added SecureFiles for atomic properties writes and owner-only POSIX permissions where supported.
- Configuration, credentials, and API usage history now use atomic replacement instead of direct overwrite.
- Legacy TomTom credentials are immediately removed from ordinary config.properties after migration.
- API usage persistence is throttled to reduce SD-card/disk writes; final counters flush at shutdown.
- Fixed TileMapPanel worker-pool leakage during Settings/dashboard rebuilds.
- Added bounded in-memory map tile cache and aged/count-limited disk cache maintenance.
- Changed persistent tile cache filenames from 32-bit String.hashCode to SHA-256.
- Added local announcement/employee image preflight limits before ImageIO decode.
- Rewrote MiniJson defensively with malformed-input checks, trailing-content rejection, and nesting-depth protection.
- Added persisted collection-count bounds when loading configuration.
- Removed unused legacy SportsScorePanel/live-score code after schedule-tracker migration.
- Reworked dense dashboard refresh methods for readability and sanitized routine logs.
- Added SECURITY_AUDIT.md documenting findings, corrections, verification, and residual deployment considerations.

## 3.0.0 — Optional authentication and protected API administration
- Added a dedicated Settings > Security tab.
- Added optional administrator login before the dashboard starts.
- Added independent optional protection for the API Providers and API Usage tabs.
- Both protections use the same local administrator password for practical kiosk administration.
- API tabs unlock only for the current Settings session and automatically re-lock when Settings is closed.
- Added a one-click Re-lock API tabs control inside the Security tab.
- Security toggles cannot be changed without the current administrator password once a password exists.
- Changing an existing administrator password also requires the current password.
- Enabling either protection requires a configured administrator password.
- Administrator passwords are never stored in plaintext.
- Added AuthService using PBKDF2WithHmacSHA256, a cryptographically random per-installation salt, 310,000 iterations, and a 256-bit derived key.
- Authentication metadata is stored separately in private auth.properties using the existing atomic/owner-only SecureFiles path.
- Password comparison uses MessageDigest.isEqual for constant-time derived-hash comparison.
- Password character arrays are cleared after use where application code controls their lifetime.
- Added process-wide failed-login throttling: five failed attempts trigger a 30-second delay that cannot be bypassed simply by closing and reopening the login dialog.
- Startup authentication fails closed if login is enabled but the authentication file is unavailable.
- Added a themed LoginDialog that matches the active application theme.
- Existing API credentials remain in the separate credentials.properties file; authentication hashes are kept in auth.properties.

## 3.0.1 — Protected API privacy shield
- Fixed sensitive API Providers/API Usage content remaining readable behind the administrator password dialog.
- Added ProtectedContentPanel, a reusable security wrapper for protected Settings pages.
- Locked API tabs now render through a privacy surface before authentication begins.
- The privacy surface downsamples the protected page, applies two heavy 9×9 blur passes, and then adds a strong opaque theme-colored veil.
- Added a centered vector padlock/status card explaining that administrator authentication is required.
- Sensitive provider names, usage values, notes, and credential fields are no longer directly visible behind the modal login prompt.
- Authentication prompt launch now occurs only after the privacy surface has been scheduled for repaint, preventing a readable protected page from remaining behind the dialog.
- Successful authentication reveals both API pages for the current Settings session.
- Re-locking API tabs immediately restores the privacy shield.
- Cancelling/closing the authentication prompt leaves the content locked and returns to the last allowed Settings tab.
- Privacy surfaces follow the active application theme.

## 3.1.0 — Multi-user access control and managed media library
- Replaced the single shared administrator-password model with named local user accounts.
- Added first-run administrator creation and one-time migration from the v3.0.x legacy administrator password.
- Added username/password login with the existing optional startup-login policy.
- Added built-in role templates: Administrator, Management, Operations, Display User, and Custom.
- Added granular permissions for General Settings, Pinned Locations, Routes, Sports, Employee Information, Operations Calendar, Dashboard Layout, Main Showcase, Media Library, API Administration, API Usage, Data & Refresh, Users & Access, and Audit Log.
- Settings navigation is permission-aware; unauthorized administration areas are not inserted into the user's Settings view.
- Settings requires authentication even when startup login is disabled, allowing the TV dashboard to remain passive while administration stays protected.
- Added Users & Access administration with Add User, Edit Access, Reset Password, Disable/Enable, and Delete User workflows.
- Added self-service My Account password changes.
- Added service-layer authorization checks so user/media administration is not protected only by UI visibility.
- Prevented the final enabled administrator from being disabled/deleted.
- Prevented a signed-in user from changing their own role/permissions mid-session; another administrator must do so.
- Added process-local authenticated SessionManager and centralized AuthorizationService.
- Added append-only local audit.log for successful login, user changes, settings saves, media changes, and protected API unlocks.
- User passwords use per-user random salts and PBKDF2WithHmacSHA256 at 310,000 iterations; plaintext passwords are never stored.
- Added managed Media Library with three collections: Announcements, Employee Photos, and Employee Showcase.
- Media imports are validated and copied into the application's own organized data directory rather than relying on arbitrary external file paths.
- Announcement and Employee Showcase images automatically participate in Main Showcase rotation.
- Team Celebrations can upload a new managed Employee Photo or choose an existing managed employee image.
- Existing legacy Main Showcase media folders and existing celebration photo paths remain compatible.
- Added Media Library authorization so employee-information users can manage employee photos without automatically receiving company-announcement media privileges.
- Added a signed-in user indicator and sign-out control in the dashboard header.
- API privacy shielding and optional API step-up authentication remain intact and now require both the proper permission and password verification.
- Removed the obsolete v3.0 LoginDialog; AuthService is retained only as a narrow legacy migration reader.

## 3.1.1 — Theme-aware Add User dialog
- Replaced the native JOptionPane-based Add User popup with a dedicated AddUserDialog.
- Add User now follows the active application theme instead of falling back to the operating system's light dialog styling.
- Username, Display Name, Password, and Confirm Password fields use the same themed input surfaces and borders as Settings.
- Role Template uses the same cross-platform themed JComboBox renderer and arrow treatment as the rest of Settings.
- Cancel/Create User buttons inherit the application theme.
- Added consistent spacing, descriptive subtitle, inline validation feedback, default-button behavior, and keyboard submission.
- Dialog automatically follows Dark, Light, holiday, seasonal, and future application themes.

## 3.1.2 — Managed-media cleanup and complete administrative theme pass
- Removed absolute employee photo paths from the Team Celebrations runtime model.
- CelebrationConfig now stores only a managed Employee Photos asset filename (`photoAsset`).
- Team Celebrations now shows a simple `Photo` column containing the managed asset name rather than an operating-system filesystem path.
- Birthday, anniversary, and Employee of the Month slides resolve employee images exclusively through the managed Employee Photos library.
- Added a one-time upgrade migration for pre-v3.1.2 `photoPath` values. Valid legacy employee images are copied into the managed Employee Photos collection, then rewritten as `photoAsset` configuration.
- Removed `celebrationMediaDirectory` and the obsolete `celebrations-media` runtime directory from AppConfig and normal persistence.
- Removed the old Main Showcase `mediaDirectory` runtime model and legacy announcement-folder Settings field.
- Added a one-time upgrade migration that imports valid images from the old announcement directory into Media Library > Announcements before the obsolete configuration property is removed.
- Main Showcase now reads only the managed Announcements and Employee Showcase collections.
- The smaller MEDIA dashboard block now reads only the managed Announcements collection and directs administrators to Settings > Media Library when empty.
- Replaced every JOptionPane-based administrative popup with the centralized themed dialog system.
- Added ThemedDialogs for themed information, warning, error, confirmation, custom-form, and multi-option modals.
- Employee Photo actions now use a fully themed options dialog and a themed managed-photo picker.
- User access editing, password reset, delete confirmation, API counter reset, media import/delete messages, Settings errors, and other administrative prompts now match the active theme.
- Added ThemedFileChooser, which explicitly constructs and themes the entire JFileChooser dialog before displaying it instead of allowing the operating system to create an unthemed wrapper.
- Location Search and Team Search dialogs now resolve the live Settings preview theme rather than only the last globally applied theme.
- Expanded ThemeStyler coverage to JList, JTree, JViewport, JToolBar, JToggleButton, JSpinner, and JFileChooser components.
- ThemedDialogs now gives the default/primary action button the active theme accent color.
- API Usage now resolves its colors from the current Settings preview theme, including custom table renderer colors.
- Static UI audit result: zero JOptionPane calls, zero unwrapped showOpenDialog calls, and zero custom administrative dialogs styled directly from stale Theme.active() state.

## 3.1.3 — Managed Media duplicate protection
- Media imports now use SHA-256 content fingerprints before copying files.
- Uploading an image that is already present in the selected Media Library collection reuses the existing managed asset instead of creating a second file.
- Added Remove Duplicates to Media Library for safe cleanup of migration-created duplicates.
- Duplicate cleanup compares actual file contents rather than filenames and always retains one copy of each unique image.
- Duplicate removals are recorded in the administrative audit log.
- Legacy/original filenames and normalized managed filenames can therefore be safely reconciled without guessing based on names.

## 3.2.0 — ORIVUE visual identity and branded application shell
- Integrated the user-supplied ORIVUE production logo and application icon as bundled classpath resources.
- Added ORIVUE as a first-class selectable application theme using the supplied palette: #0D1117, #162033, #1E2A44, #2563EB, #4DA8FF, #A8B3C7, #E6EDF5, and #FFFFFF.
- New installations now default to the ORIVUE theme; existing installations preserve their saved theme selection.
- Added a branded startup splash screen with the ORIVUE logo, subtle radar/intelligence rings, status text, and a blue progress indicator.
- Moved configuration loading off the Swing event thread so the splash remains responsive during startup.
- Completely redesigned username/password login around the ORIVUE identity with branded logo treatment, dark operations card, large Sign In action, show/hide password control, lockout feedback, and administrator-reset guidance.
- Redesigned first-administrator provisioning and legacy-password migration screens to match the ORIVUE secure-access experience.
- Added runtime macOS/Windows/Linux window icons from the supplied ORIVUE app icon.
- Added best-effort macOS dock/taskbar icon integration.
- Rebranded the main application window and Settings title as ORIVUE Operations Intelligence.
- Added a compact ORIVUE brand lockup to the dashboard header while preserving the site-configurable operational header text.
- Added a branded ORIVUE administration strip to Settings showing the active signed-in user and role.
- Added application-wide primary-action styling; branded primary actions use the ORIVUE #2563EB blue and #4DA8FF highlight.
- Updated Save & Apply, Add User, login, setup, and migration primary actions to use the shared branded action treatment.
- All previous Dark, Light, holiday, and seasonal themes remain available; the ORIVUE product shell and iconography remain consistent around them.
- Updated product-facing names and persistence comments from Weather & Traffic Monitor to ORIVUE while retaining legacy application-data paths for safe upgrade compatibility.
- Updated build.sh so bundled ORIVUE resources are automatically copied into the runnable JAR.

## 3.2.1 — Polished authentication and seamless brand lockups
- Reworked the ORIVUE login screen to more closely match the commercial product mockup while remaining fully Swing-based.
- Replaced standard login text fields with custom-painted rounded ORIVUE input controls.
- Added integrated vector username and lock icons directly inside the authentication fields.
- Added an integrated vector eye control inside the password field for show/hide behavior.
- Added focus-state blue glow/border treatment using the ORIVUE Sky Blue accent.
- Replaced the standard primary login button with a custom blue-gradient ORIVUE Sign In button including hover/pressed states.
- Tightened login-card proportions and removed the large visible Cancel action; Escape still exits the dialog.
- Added a text-style Forgot Password action that routes users to administrator-reset guidance.
- Added a soft custom-painted shadowed authentication card to reduce the native Swing form appearance.
- Added best-effort macOS full-window/transparent-title-bar integration while retaining native window controls.
- Created a background-free ORIVUE symbol asset from the supplied production artwork.
- Added OrivueBrandLockup, a reusable symbol + live-rendered wordmark component that stays crisp across DPI/resolution changes.
- Dashboard header no longer places the square app-icon tile on top of another card; it now uses the transparent ORIVUE mark directly on the themed header surface.
- Added a cleaner horizontal ORIVUE/product-header lockup with a subtle divider before the site-configurable operations title.
- Settings header now uses the background-free ORIVUE symbol instead of the square app tile.
- Splash screen, initial administrator setup, and legacy account migration now use the same seamless brand-lockup system.
- The supplied horizontal ORIVUE reference informed the new live-rendered header lockup; live text is used instead of a screenshot so the wordmark remains sharp and better integrated at every scale.

## 3.2.2 — Secure password masking fix
- Fixed the ORIVUE login password field displaying plaintext by default.
- Root cause: the custom field installed BasicTextFieldUI on JPasswordField, bypassing Swing's password echo rendering.
- Password fields now retain a BasicPasswordFieldUI delegate while preserving the custom ORIVUE border, placeholder, icon, and typography.
- Password masking now defaults explicitly to the bullet character rather than relying on platform look-and-feel initialization.
- The integrated eye control now deterministically switches between masked and visible modes.
- Eye-button tooltip changes between Show password and Hide password.
- Visibility changes force the password field to repaint immediately.

## 3.2.3 — Compact header branding and clean rounded app icon
- Replaced the wide dashboard ORIVUE wordmark lockup with the transparent ORIVUE symbol only.
- The site-configurable operational header now begins immediately beside the symbol, freeing significant horizontal room for date/time, signed-in user, sign-out, and Settings controls.
- Simplified the Settings administration header to the same compact single-symbol treatment.
- Rebuilt the application icon from the existing ORIVUE artwork by cropping away the outer opaque square canvas.
- Applied true alpha transparency outside the finished rounded app-tile silhouette.
- Rescaled the completed rounded tile back to the full production icon size for cleaner macOS Dock/taskbar presentation.

## 4.0.0 — Modular Operations Workspace
- Added a parallel v4 Operations Workspace while preserving the complete Classic Information Display dashboard.
- Added Settings > General > Dashboard Experience with Operations Workspace and Classic Information Display choices.
- Switching dashboard experience through Save & Apply hands off immediately without deleting either dashboard implementation or its settings.
- Added DashboardLauncher as the single runtime selection boundary between Classic and Workspace experiences.
- Completely redesigned the primary dashboard around an operations-application shell inspired by the approved ORIVUE v4 visual concept.
- Added compact branded top navigation with active-alert indicator, authenticated user/role, and Settings access.
- Added left-side operations navigation for Dashboard, Weather, Traffic, Operations Calendar, Team Celebrations, Pinned Locations, Routes, Sports, Main Showcase, and Workspace Setup.
- Added time-aware personalized greeting and live summary strip.
- Added modular Local Weather card using the existing Open-Meteo provider and current primary site location.
- Added modular Traffic Map card using the existing TileMapPanel, TomTom traffic layer, RainViewer radar, and NWS alert polygons.
- Added live traffic summary based on the worst configured route delay.
- Added Upcoming Events card driven by the Operations Calendar.
- Added Team Celebrations card using managed employee photos and upcoming birthday/anniversary/Employee of the Month data.
- Added a new Operations Snapshot module with configurable KPI cards.
- Added default KPI definitions for LHY Performance, Lines Shipped, Damages, Floor Denials, and Active Alerts.
- Active Alerts can use the built-in SYSTEM_ALERTS data source; other v4 KPI cards currently use MANUAL values until SQL/report integrations are connected.
- Added configurable KPI target logic: Higher is better or Lower is better, with target-met/below-target status rendering.
- Added Settings > Operations Workspace for enabling/disabling individual dashboard modules and adding/removing KPI cards.
- Added stable KPI dataSourceId values so future SQL/API metric providers can populate the same dashboard cards without redesigning the UI.
- Updated configuration persistence for dashboard experience, workspace modules, and up to 24 KPI definitions.
- Redesigned the login screen into the new v4 two-pane product/authentication layout while retaining secure password masking, failed-attempt throttling, and the local user service.
- Existing authentication, permissions, Media Library, API security, holiday themes, Operations Calendar, Team Celebrations, and Classic dashboard features remain available.

## 4.0.1 — Workspace layout and Settings usability refinement
- Reworked the Operations Workspace dashboard grid with deliberate weather/map/right-column proportions and a fixed Operations Snapshot band.
- Rebuilt Local Weather layout so current conditions, icon, high/low/wind/gust stats, and hourly forecast remain structured instead of stretching across tall displays.
- Refined custom ORIVUE login fields with symmetric internal padding and centered field geometry for username/password text and icons.
- Made Operations Workspace KPI cells explicitly editable.
- KPI Data Source editor is now editable, allowing future custom SQL/report provider identifiers in addition to MANUAL and SYSTEM_ALERTS.
- Add KPI now creates, selects, scrolls to, and immediately begins editing the new metric row.
- Remove KPI now terminates active table editing cleanly before deleting the selected row.
- Increased the KPI editor viewport and configured practical column widths.
- Added vertical scrolling to ordinary Settings pages so lower controls remain reachable without manually resizing the Settings window.
- Settings pages track viewport width and scroll vertically only, preserving the unified ORIVUE administration layout.

## 4.0.2 — Reference-aligned workspace cards
- Reworked the Operations Workspace module flow so the dashboard grid keeps a deliberate reference-style height instead of stretching vertically to consume every spare fullscreen pixel.
- Local Weather now follows the ORIVUE workspace reference: compact current conditions, large weather icon, High/Humidity/Low/Wind statistics, separators, and a five-day forecast strip.
- Added actual current relative humidity from Open-Meteo to the shared WeatherSnapshot model.
- Added five daily forecast points to WeatherSnapshot and OpenMeteoService using the existing seven-day provider request.
- Increased retained hourly forecast data from 12 to 48 points for future workspace/weather pages.
- Upcoming Events now uses compact icon tiles, stacked title/date/type rows, separators, and a View Full Calendar action.
- Team Celebrations now uses recognition icons, circular managed employee photos, compact recognition/name/date rows, separators, and a View All action.
- Upcoming Events and Team Celebrations are limited to polished dashboard summaries while their complete data remains available through the existing administration pages.
- Tightened Operations Snapshot and primary workspace sizing so the home screen more closely matches the supplied ORIVUE operations-application reference.

## 4.1.0 — Workspace navigation and configurable information strip
- Increased the Operations Workspace dashboard scale to better match the supplied ORIVUE reference layout on 1080p and 4K displays.
- Expanded the primary Weather / Traffic Map / Events / Celebrations dashboard band to a deliberate 520px reference-scale height.
- Increased Weather typography, weather-condition icon sizing, event icon tiles, celebration avatars, and right-column typography.
- Added a configurable Information section directly above Operations Snapshot.
- The new Information section reuses Classic Dashboard Block selections so existing route/weather/alerts/wind/forecast/media/status configuration remains valuable in v4 instead of creating a second incompatible system.
- Added independent v4 Information Block count (2–6) and enable/disable control under Operations Workspace configuration.
- The default v4 Information strip surfaces the first configured Classic block choices; existing default configuration therefore starts with primary weather and commute routes.
- Compact v4 information cards support route travel time/delay/status, current weather, severe alerts, today's high/low, wind/gusts, announcement count, sports configuration, and system status.
- Information cards update automatically when weather, traffic, or alert data refreshes.
- Rebuilt the v4 sidebar as the Operations Workspace navigation/settings system.
- Clicking a v4 sidebar item now replaces the right-side workspace content with that individual administration page instead of opening the full Settings window.
- Added workspace routes for Weather, Traffic & Routes, Operations Calendar, Team Celebrations, Pinned Locations, Routes, Sports, Main Showcase, Operations Workspace, Information Blocks, Media Library, Users & Access, General, Data & Refresh, API Providers, API Usage, and My Account.
- Sidebar items remain authorization-aware and are hidden when the signed-in user lacks the required permission.
- Protected API pages retain password step-up authentication when configured.
- Embedded workspace settings pages reuse the exact same SettingsDialog controls, validation, persistence and permissions as Classic; business/configuration logic is not duplicated.
- Added per-page Save & Apply, Discard Changes, and Back to Dashboard actions inside the workspace.
- Classic Information Display continues using the existing complete Settings popup and was not converted to sidebar administration.
- Inactive live map/dashboard UI resources are released when navigating to an administration page and recreated when returning to Dashboard.

## 4.1.1 — Compact information carousel and larger rotating showcase
- Reduced the v4 Information section to roughly the same visual scale as Operations Snapshot.
- Removed the oversized nested information-card treatment; Information now presents compact metric-style items inside one parent card.
- Added optional overflow auto-rotation for Information Blocks.
- Administrators can choose how many information items are visible at once (2–6), enable/disable automatic rotation, and select a 5–60 second rotation interval.
- When more Information choices exist than fit at once, ORIVUE pages through them automatically without increasing dashboard height.
- Added a subtle page-range indicator such as `1–4 of 10 • AUTO`.
- Shrunk the Local Weather column width and increased the center dashboard region from 50% to 56% of the primary row.
- Replaced the v4 map-only center card with the proven MainShowcasePanel used by Classic.
- The larger v4 center region can now rotate between the live traffic/weather map, managed announcement media, Operations Calendar slides, and active team-recognition slides using the existing Main Showcase interval.
- Automatic severe-weather map priority is preserved in v4: qualifying severe alerts can pin the live map and suspend media rotation when configured.
- Main Showcase media enablement, rotation interval, severe-weather priority, and Media Library content remain shared with Classic rather than being duplicated.
- Added holiday-aware visual event tags to Upcoming Events. Christmas, Thanksgiving, Independence Day, Halloween, Valentine's Day, St. Patrick's Day, Labor Day, Memorial Day, Veterans Day, and New Year receive appropriate locally rendered imagery; custom/company events receive a generic calendar tag.
- Increased Upcoming Event tag tiles for stronger visual recognition.
- Increased Team Celebration recognition icons and managed employee-photo avatars to better match the ORIVUE reference design.

## 4.1.2 — Dashboard proportion and recognition-icon polish
- Converted the Local Weather module into a compact square-style card that stays top-aligned rather than stretching to the full height of the primary dashboard row.
- Tightened current-condition typography, weather icon size, statistics, and five-day forecast spacing so the Weather card remains readable within its square footprint.
- Increased the primary workspace row from 520px to 580px so Main Showcase receives more vertical room for maps, announcements, calendar notices, and celebration slides.
- Increased the Main Showcase map/media preferred canvas to 760x500 while preserving responsive resizing.
- Team Celebration recognition tiles are now full 64x64 squares to visually balance the employee photo.
- Birthday recognition now uses a locally rendered wrapped-present icon.
- Anniversary recognition now uses a locally rendered confetti-popper icon with multicolor confetti.
- Expanded holiday-aware Upcoming Event imagery with clearer semantic icons:
  - Thanksgiving: turkey
  - Christmas: evergreen tree
  - Independence Day / Fourth of July: fireworks
  - Labor Day: American flag
  - Memorial Day: salute
  - Veterans Day: American flag + star
  - New Year: fireworks
  - Halloween: pumpkin
  - Valentine's Day: heart
  - St. Patrick's Day: clover
  - Martin Luther King Jr. Day: dove
  - Presidents Day: American flag
  - Easter: decorated egg
  - Juneteenth: American flag + gold star
  - Custom/company events: generic calendar
- All event and celebration imagery is rendered locally with Swing/Java2D and requires no external image API or new media files.

## 4.1.3 — Information strip alignment, live sports preview and priority weather alert
- Reworked v4 Information metrics to use the same evenly distributed, centered visual rhythm as Operations Snapshot.
- Each visible Information item now occupies an equal-width slot with centered icon/title, primary value and supporting status text.
- Retained the compact Information carousel and overflow auto-rotation introduced in v4.1.1.
- Added a dedicated lightweight sports refresh to Operations Workspace using the existing TheSportsDB adapter.
- Sports Information items now load the configured team's actual badge/logo instead of the generic status/check icon.
- Sports Information primary value now shows the next scheduled game's date.
- Sports supporting text now shows `vs` / `at` opponent plus the local start time.
- If the configured team badge is not included in the event response, ORIVUE falls back to the existing numeric team lookup used by the Sports subsystem.
- Severe Weather Information now displays the highest-priority active NWS alert name as the primary value rather than only the number of alerts.
- Supporting Severe Weather text retains severity and the total active-alert count.
- Added operational weather-alert prioritization so Tornado Emergency/Warning, Extreme Wind Warning, Severe Thunderstorm Warning, Flash Flood Warning, watches, heat warnings/advisories and other alerts are ordered by urgency/severity rather than provider response order.
- High-priority warning detail text uses the danger treatment; lower-priority watches/advisories use the warning treatment.

## 4.1.4 — Celebration and calendar glyph refinement
- Reworked Team Celebration iconography around the supplied bold glyph/silhouette references.
- Birthday recognition now uses a filled wrapped-present glyph with strong negative-space ribbon details.
- Anniversary recognition now uses a filled party-popper glyph with bold multicolor confetti shapes that remain legible at dashboard scale.
- Calendar/holiday event tags continue using locally rendered semantic glyphs (turkey, Christmas tree, fireworks, flag, salute, pumpkin, heart, clover, dove, Easter egg, etc.) so they remain crisp, theme-aware, and free of stock-image backgrounds/watermarks.
- All glyphs are original Java2D application artwork inspired by the supplied visual style rather than embedded copies of the reference sheets.

## 5.0.0 — Employee Operations, secure call-ins, assignment planning and alternate identity
- Added management-only Employee Operations as the authoritative employee system of record.
- Added employee identity fields for employee number, WMS short name, department, shift,
  hire date, birthday, phone, managed employee photo, active state and recognition preferences.
- Added secure per-employee call-in PINs stored as salted PBKDF2-HMAC-SHA256 hashes.
- Added Training & Qualifications records with categories, completion/expiration dates,
  trainer, status and notes.
- Added Attendance & Call-In history for call-outs, late arrivals, early departures,
  absences, scheduled absences and other events.
- Added arbitrary Performance history records for future LHY/pick/move/quality/report feeds.
- Added global duty requirements and qualification-aware Daily Assignment recommendations.
- Assignment recommendations automatically exclude whole-day call-outs and expired qualifications.
- Existing Team Celebrations employee records migrate once into Employee Operations.
- Birthday/anniversary/Employee of the Month Main Showcase data is now projected from
  Employee Operations instead of being maintained as a second editable employee database.
- Classic Settings no longer overwrites Employee Operations recognition data from the retired table.
- Added Employee Operations permissions with Management and Administrator defaults.
- Existing Management accounts inherit the new employee permissions automatically.
- Added LOCAL_TEST employee call-in mode requiring no telephone provider.
- Added optional Twilio Voice webhook server with employee-number + PIN authentication.
- Added Twilio X-Twilio-Signature validation before production call-in webhooks are processed.
- Added Twilio Programmable Messaging management SMS notifications.
- Added SendGrid Mail Send management email notifications.
- Twilio/SendGrid secrets are stored in the protected API credential file.
- Added Twilio and SendGrid to local API usage accounting.
- Added an optional North Star • Silver / Black application identity based on the supplied
  alternate visual direction while retaining ORIVUE as a selectable theme.
- North Star includes a locally rendered metallic three-point-star identity, silver/black palette,
  dynamic top-bar/login/splash branding, themed login controls and compatible window icons.
- Updated birthday/anniversary glyphs to the supplied bold silhouette language:
  wrapped present for birthdays and party-popper/confetti for anniversaries.
- Existing holiday event glyphs remain semantic locally rendered calendar artwork.

## 5.0.1 — North Star startup preview, glyph replacement, and Information strip correction
- North Star is now the default theme for brand-new installations.
- Existing installations receive one automatic North Star preview on the first v5.0.1 launch so the
  North Star splash and login screens are visible immediately.
- After that one-time preview, any theme selected and saved by the user is respected normally.
- Removed the legacy colored Calendar event icon treatment.
- Upcoming Events now use neutral theme-aware tiles with bold monochrome glyph artwork in the supplied
  silhouette/glyph-sheet visual language.
- Removed the colored birthday/anniversary glyph treatment.
- Birthday recognition now uses a bold monochrome wrapped-present glyph.
- Anniversary recognition now uses a bold monochrome party-popper/confetti glyph.
- Thanksgiving turkey, Labor Day flag, Christmas tree, Fourth of July/New Year fireworks, Memorial
  Day salute, Halloween, Valentine, St. Patrick's, Easter and related Calendar symbols now use the
  same monochrome glyph language rather than the former mixed-color icon system.
- Rebuilt Information metrics to use the same label/value/status vertical rhythm as Operations Snapshot.
- Fixed the expanding heading panel that could consume the Information metric height and hide its
  primary value/detail.
- Information items remain equal-width through the existing GridLayout and auto-rotation system.
- Route information now reliably presents the configured route name, live travel time, traffic status,
  and delay information in the visible metric.
- Sports information uses the configured team's actual logo/badge, next game weekday/date, opponent,
  home/away indicator, and local start time.
- Sports displays a visible loading state while the upcoming-game request is still completing.

## 5.0.2 — Shared metric geometry, universal North Star surfaces, and approved-glyph pipeline
- Information and Operations Snapshot now use the same horizontal column geometry.
- Information slot 1 is directly above KPI slot 1, slot 2 above KPI slot 2, etc.
- If fewer Information items are visible than KPI columns, transparent placeholder columns are used
  instead of stretching the visible Information items across the entire row.
- Information preserves the configurable visible-count and overflow auto-rotation behavior.
- Route metrics retain route name, live travel time, traffic state and delay.
- Sports metrics retain the actual configured team badge plus next game date, opponent and local time.
- Removed remaining hard-coded ORIVUE navy/blue workspace surfaces from the Operations Workspace shell.
- Top bar, sidebar, cards, borders, embedded settings surfaces, direct settings pages and utility buttons
  now derive their surface colors from the active AppTheme.
- Corrected ThemeStyler primary-button borders so non-ORIVUE themes no longer inherit ORIVUE sky blue.
- First-admin, legacy-admin-migration and login surfaces now follow the selected theme palette.
- North Star therefore uses graphite/black/silver surfaces rather than a black + ORIVUE-navy mixture.
- Added WorkspaceGlyphs, an asset-backed glyph system for Calendar and Team Celebrations.
- Exact approved transparent PNG glyphs can now replace the Java2D fallback simply by bundling the
  documented filename under resources/glyphs; no dashboard code change is required.
- Glyph assets are scaled and tinted to the active theme while preserving alpha/antialiasing.

## 5.0.3 — Information ticker, universal settings polish, restored holiday overlays and North Star header
- Information data typography now scales down responsively for longer route names, dates and alerts.
- Reduced Information metric vertical padding so label/value/detail no longer clip inside the compact strip.
- Retired implicit Information page cycling on upgrade; v5.0.3 defaults the Information row to Static.
- Added three explicit Information movement modes: Static, Paged Rotation and Continuous Ticker.
- Continuous Ticker scrolls the complete configured Information sequence smoothly as one horizontal strip.
- Added configurable continuous-ticker speed in pixels per second.
- Paged Rotation remains available only when explicitly selected.
- Information movement timers are disposed cleanly during dashboard/settings rebuilds.
- Expanded ThemeStyler into a universal Swing settings skin for text/password fields, text areas,
  combo boxes, checkboxes, radio buttons, buttons, tables, lists, tab panes, split panes,
  scroll panes, spinners, separators and rounded surfaces.
- Employee Operations therefore no longer falls back to native light/gray Swing tabs, fields and tables.
- The same recursive settings styling is inherited by other Settings and embedded workspace pages.
- Added the holiday/celebration OverlayEffectsPanel to Operations Workspace, not just Classic Dashboard.
- Main Showcase celebration slides in Operations Workspace now trigger the same finite confetti engine.
- Automatic severe-weather priority suppresses modern-workspace decorative overlays consistently.
- North Star branding and holiday effects are now independent: North Star can remain the interface
  identity while automatic seasonal overlays use Christmas/Halloween/Thanksgiving/etc. animation.
- Modern Workspace now refreshes the top-bar/window/application brand icon during UI rebuilds.
- Polished the generated North Star symbol with metallic ring, blue horizon arc and bright focus glint
  to better reflect the supplied North Star identity reference.

## 5.0.4 — North Star reference pass, logo artifact removal and settings layout polish
- Applied the supplied North Star reference set as the authoritative branding direction.
- North Star wordmark now uses NORTHSTAR with OPERATIONS INTELLIGENCE.
- Updated the North Star palette around the supplied reference colors:
  electric blue accent #0D6EFD, graphite #2B2F36, gray #6B7280,
  light silver #D1D5DB and white, while keeping primary application surfaces black.
- Rebuilt the reusable North Star symbol with a transparent background, metallic monitoring ring,
  three-point directional star and tightly contained top sparkle.
- Removed the experimental horizon arc and cross-shaped glint rays that produced stray lines
  across splash, login, top-bar and other logo usages.
- Removed North Star radar/horizon guide lines from the shared splash/login backdrop.
- North Star splash/login surfaces now retain only a restrained depth glow behind the clean mark.
- Added a dedicated rounded-square North Star application icon for window/taskbar/dock usage.
- Operations Workspace continues to rebuild the header identity from the active theme so North Star
  is reflected in the top application logo instead of the ORIVUE lockup.
- Employee Operations forms now track viewport width and no longer create horizontal scrollbars
  merely because a row contains long credential/security text.
- Reorganized Call-In Integration into clear Call-In Service, Twilio Voice & SMS,
  Management Email and Local Call-In Test sections.
- Standardized Employee Operations field-label widths, row heights, padding and control heights.
- Long production-security text now wraps instead of forcing the settings pane wider.
- Added in-app E.164 guidance for Twilio telephone values while preserving friendly formatting
  for ordinary employee-profile phone display.
- Retains v5.0.3 Static/Paged/Continuous-Ticker Information modes, universal settings styling,
  modern-workspace holiday overlays and celebration-confetti integration.

## 5.0.5 — Employee recognition consolidation and automatic phone normalization
- Removed the duplicate Team Celebrations settings route from the Operations Workspace sidebar.
- Employee Operations is now the single management settings destination for employee identity and recognition preferences.
- The Team Celebrations dashboard card remains available as an operational display module, but its View All action opens Employee Operations.
- Added a per-employee master Celebration announcements preference.
- Employees who opt out remain active employees and retain birthday/hire-date data, but no birthday, anniversary, or Employee of the Month announcement is generated for them.
- Birthday recognition, Anniversary recognition, and Employee of the Month remain individual options beneath the master employee celebration preference.
- Added centralized telephone normalization in PhoneNumbers.
- Employee profiles accept normal U.S. entry formats such as 205-799-9890, (205) 799-9890, 2057999890, 1-205-799-9890, or +12057999890.
- Valid U.S. employee phone entries are saved canonically as E.164 and displayed back in the UI as (205) 799-9890.
- Twilio SMS recipient and from-number values are normalized to E.164 immediately before provider requests, so punctuation in locally configured numbers cannot break messaging.
- Invalid telephone entries are rejected with a clear validation message before the employee profile is saved or a Twilio request is attempted.
- Existing Employee Operations files remain compatible; the new celebration-announcement preference defaults to enabled when the field does not exist.

## 1.0.3 — Full-resolution North Star identity and Call-In layout polish
- Replaced all derived/cropped North Star brand sources with the new full-quality approved primary logo, splash screen and app icon artwork.
- Added a single canonical brand pipeline: every header/window/Dock icon is downscaled directly from the original high-resolution source, never from a previously resized image.
- Added progressive high-quality downscaling for small North Star marks to improve 16–64 px Retina rendering.
- The startup window now paints the exact supplied North Star splash artwork and overlays the live progress value on its integrated progress track.
- The login and setup flows use the exact full-resolution primary North Star logo asset.
- The application/window icon uses the supplied high-resolution app-icon artwork with only the external presentation-white canvas removed.
- Reworked Employee Operations > Call-In Integration section headers with protected 52 px header rows, larger vertical separation, and independent separators so Call-In Service, Twilio Voice & SMS, Management Email and Local Call-In Test cannot collapse into adjacent controls.
- Increased Employee Operations form row padding for a cleaner, more legible layout at 1080p/Retina sizes.


## 1.0.4 — KPI semantics, active navigation, HiDPI header branding and approved glyphs
- Corrected Operations Snapshot status semantics for exception KPIs.
- Damages, Floor Denials and Active Alerts are always evaluated as lower-is-better metrics.
- A missed lower-is-better target now reads ABOVE TARGET instead of BELOW TARGET.
- Legacy saved KPI direction values no longer cause incorrect green/amber status logic.
- Added rounded active-state highlighting to the North Star sidebar so the current workspace page is immediately visible.
- Dashboard, settings routes and direct Employee Operations pages all keep the sidebar selection synchronized.
- Reworked the North Star compact symbol as a HiDPI-aware Swing icon that paints from the full-resolution canonical source instead of a pre-rendered 42 px bitmap.
- Vertically centered the NORTH STAR / OPERATIONS INTELLIGENCE text beside the header symbol.
- Added the supplied approved glyph assets: confetti, toast, food, Halloween, Christmas, Good Friday, fireworks, Easter, Thanksgiving and American flag.
- Anniversary recognition now uses the supplied confetti glyph.
- Employee-of-the-Month recognition can use the supplied toast/recognition glyph.
- Calendar events now map the supplied holiday/event glyphs by semantic name, with existing fallback rendering retained for events without an approved asset.


## 1.0.5 — Unified Information controls, working continuous ticker and HiDPI dashboard glyphs
- Fixed the Continuous Ticker lifecycle bug that destroyed its viewport/track immediately before the timer started.
- Continuous Ticker now scrolls every configured Information item and loops the duplicated track continuously.
- Information Blocks is now the single configuration page for configured item count, visible-at-once count, movement mode, page interval and ticker speed.
- Operations Workspace retains only the Information row enable/disable checkbox and points administrators to Information Blocks for configuration.
- Expanded visible-at-once choices from 2–6 to 2–8 so the Information row can align with up to eight Operations Snapshot KPI columns.
- Configured Information sets remain 6/8/10/12 items. A 12-item set can now move through an 8-item viewport as a continuous loop.
- Converted supplied holiday/event glyphs to full-resolution HiDPI painting instead of pre-rendering 40 px bitmap icons.
- Converted the built-in birthday gift glyph to display-time vector painting for Retina/HiDPI clarity.
- Thanksgiving, American-flag/Labor Day, anniversary confetti and other supplied glyphs now remain crisp in Upcoming Events and Team Celebrations.


## 1.0.6 — Workspace consolidation, larger media preview and vector branding
- Combined Dashboard Blocks/Information controls and Operations Workspace into one Workspace Setup tab.
- Enlarged Media Library presentation preview and allowed high-resolution media to fill the available preview region.
- Changed the sample phone number to 123-456-7890.
- Replaced the small raster header/Dock symbol with a resolution-independent North Star vector mark.
- Removed the legacy white lower-edge artifact from the app icon.


## 1.0.7 — Workspace Setup routing fix
- Removed the obsolete Information Blocks item from the main administration sidebar.
- Corrected Workspace Setup to route to the unified Workspace Setup settings page.
- Fixes the false "Page Unavailable" message for administrators caused by stale pre-merge tab names.


## 1.0.8 — Information Block selector sizing
- Increased the Information Row & Dashboard Layout section inside Workspace Setup.
- Added a dedicated, titled selector viewport with enough height to display the configured Information Block selectors.
- Retained vertical scrolling for 10/12-item configurations and smaller displays instead of collapsing the selector list to a single row.

## 1.1.0 — Sports information + bulk CSV employee updates
- Sports Information blocks now fit team badges inside the available icon box without cropping or forced aspect-ratio distortion.
- Increased the Sports value row height so badge artwork is fully visible.
- Sports blocks now show the full opponent on the primary line and the complete local schedule date/time on the detail line, removing the previous 22-character opponent truncation.
- Added Employees → Import / Update CSV for bulk employee upserts using EmployeeNumber as the stable key.
- Blank CSV cells preserve existing values; existing IDs, secure call-in PIN hashes, training, attendance, and performance records remain intact.
- Added an Employee CSV Template action and import preview/confirmation summary.
- Added reusable `com.wtm.importer` CSV parsing, handler, and result classes as the foundation for future daily KPI CSV imports such as LHY, picks, floor denials, and other operational metrics.


## 1.1.1 — Information row sizing and clipping fix
- Matched the dashboard Information band height to the Operations Snapshot band at 132 px.
- Increased the Information ticker viewport and metric height so sports opponent/date/time, weather, routes, and system-status content no longer clips vertically.
- Added consistent minimum heights to both Information and Operations Snapshot bands for stable layout behavior across display sizes.
- Added extra vertical padding inside Information metrics while preserving the existing horizontal column/ticker behavior.


## 1.1.2 — Information layout fit + visible-count persistence
- Increased both Information and Operations Snapshot dashboard bands to 156 px so their visual scale remains matched while providing enough room for all Information metric lines.
- Increased Information ticker metric/viewport height to 116 px and vertically centered each metric, preventing route status and sports game date/time text from being clipped.
- Fixed a configuration persistence defect where `Visible at once = 8` was saved correctly but clamped back to 6 during application startup.
- Synchronized the Information visible-count load limit with the existing Workspace Setup UI/save limit of 8.


## 1.1.3 — Information fit and balanced lower dashboard bands
- Reworked the Information band instead of simply increasing its height: removed the ticker mode SOUTH label that was shrinking the usable metric viewport and clipping the detail line.
- Matched Information and Operations Snapshot at a compact 144 px card height.
- Reduced ticker metric/viewport height to 92 px so its fixed content size fits inside the actual BorderLayout CENTER region.
- Tightened Information line spacing while retaining all three lines: title, primary icon/value, and detail/status.
- Vertically centered Operations Snapshot KPI content with equal flexible space above and below to eliminate the large unused lower area.
- Updated dashboard height calculations for the compact matched bands.


## 1.1.4 — Animated North Star startup + integrated fading login
- Replaced the synthetic static startup progress screen with the supplied `NORTHSTAR loading animation.mp4` rendered as an embedded one-shot 12 fps frame sequence.
- The animation plays once and holds its exact final frame instead of looping.
- Added a secure login card directly to the startup surface; it begins a smooth eased fade during the final portion of the animation and becomes interactive as the fade completes.
- The login is overlaid on the lower portion of the animation so the video canvas never jumps, resizes, or disappears when authentication becomes available.
- Startup authentication continues to use the existing UserService lockout, password verification, session, and audit-log infrastructure.
- First-run administrator setup and login-disabled startup still wait for the complete branded animation before transitioning.
- The startup architecture is ready for a future looping finished-state animation without changing the authentication flow.


## 1.1.5 — Startup login overlay visibility fix
- Fixed the animated startup login being hidden behind the full-screen animation surface.
- Explicitly raises the fading secure-login panel above the animation using Swing component z-order.
- Keeps the supplied animation playing once, holds its final frame, and fades the login over that frame near the end.
- Increased the login overlay height slightly so username, password, sign-in, and status content fit comfortably.


## 1.1.6 — Aspect-ratio startup layout + dedicated fading login region
- Increased the startup window to provide a dedicated authentication area beneath the loading animation.
- The supplied North Star loading animation now retains its original aspect ratio inside the upper region.
- Reserved the login region from the first frame so the animation never jumps or resizes when authentication fades in.
- The login now fades into a clean lower black panel instead of covering the NORTHSTAR / OPERATIONS INTELLIGENCE branding.
- Preserved the one-shot animation behavior and final-frame hold from v1.1.5.


## 1.1.7 — Interactive animated-login input fix
- Fixed the startup login fields appearing visible but not accepting mouse/keyboard interaction on macOS.
- Explicitly makes the startup JWindow focusable and auto-focus capable.
- Activates the startup window and requests initial editor focus when the fade completes.
- Makes controls interactive once the login is visibly faded in instead of waiting until the last few animation frames.
- Makes the entire rounded username/password field shell clickable, including icon and padding areas, by forwarding clicks to the underlying text editor.
- Propagates enabled/disabled state through each custom North Star login field, including the password visibility control.


## 1.1.8 — macOS startup authentication focus fix
- Replaced the startup JWindow with an undecorated JFrame while preserving the same full-screen branded appearance.
- Fixes macOS cases where the visible startup login could not become a native key/focus window, leaving username/password fields unclickable.
- Explicitly enables Swing focus traversal and native focus acquisition for the startup frame.
- Makes text editors explicitly focusable/request-focus enabled.
- Shell/icon clicks now transfer focus synchronously to the actual editor.
- Login controls become interactive earlier in the fade while the animation still finishes and holds its final frame.

## 1.1.9 — KPI CSV imports, active-alert dropdown, and dashboard centering
- Vertically centers Information and Operations Snapshot metrics against the complete card height, including the title band.
- Adds a generic profile-based KPI CSV import pipeline to Operations Workspace settings.
- Adds import support for the supplied Daily LHY / LPH report: latest completed LHY maps to LHY Performance and latest completed Order Lines maps to Lines Shipped.
- Adds import support for the supplied Floor Denials report, deduplicating multiple RF task rows that belong to the same business denial by day/order/line/item/type.
- Stores imported daily KPI values in a generic long-form kpi-history.csv under the North Star application-data folder so future KPI reports can reuse the same architecture.
- Adds an Import KPI CSV preview/application control and persistent imported-data summary to Operations Workspace settings.
- Makes the top alert count clickable; the dropdown lists active NWS alerts by priority and opens alert details on selection.


## 1.1.10 — Themed KPI file browser and import progress
- Replaces the macOS JFileChooser used by KPI imports with a dedicated North Star themed CSV browser.
- Fixes overlapping Name / Date Modified rendering caused by platform file-chooser delegates.
- Adds Home, Documents, Up-folder navigation, CSV-only filtering, file metadata, double-click navigation, and consistent North Star styling.
- KPI preview now uses the themed application dialog path.
- Separates KPI preview from commit so KPI history is not changed until the user confirms Import.
- Adds a themed determinate progress bar showing validation, history update, dashboard application, and completion stages.

## 1.1.11 — Workspace polish, LHY rollups, employee damage attribution
- Standardizes tab content and sub-blocks on a rounded, single-pane design.
- Removes the extra tab content outline that created a double-border appearance.
- LHY imports populate Previous Day, Monthly Average, Quarterly Average, and Annual Average when enough daily data exists.
- Keeps legacy LHY Performance populated for backward compatibility.
- Adds damage CSV KPI recognition and maps matched employee damage rows into Employee Operations > Performance.
- Matches damage employees by employee number, WMS/short name, or full name and suppresses duplicate imported incidents.

## 1.1.12 — Dedicated Call-In workspace
- Adds a first-class Call-In route to the North Star sidebar using the same rounded navigation highlight, spacing, borders and active theme as the rest of the application.
- Moves system-level Twilio, SendGrid, webhook, notification and master enable/disable controls out of individual Employee Operations records.
- Employee Operations now retains only employee-specific call-in data: employee phone, Call-In PIN, and per-employee Attendance & Call-Ins history.
- Adds Call-In Activity with today summary cards and recent employee call-ins.
- Adds searchable Call-In History with employee, type, source, status, caller and notes.
- Adds centralized Call-In Settings with master service toggle, LOCAL_TEST/TWILIO_WEBHOOK/OFF modes, secure credentials, management recipients and listener status.
- Adds a dedicated Testing tab for PIN validation, local call-in simulation and optional management notification tests.
- All new Call-In screens use North Star themed controls, rounded cards, themed tables/scroll panes and themed message dialogs.


## 1.1.14 — Integrated site header, severe-weather ticker, alert popup polish
- Moves the configured site/location header and dashboard ticker into the permanent top application chrome.
- Removes the duplicate dashboard header card.
- Replaces the old viewport ticker with a paint-driven continuous ticker that scrolls reliably.
- Active NWS alerts automatically override normal ticker messaging and return to configured text after alerts clear.
- Rebuilds the Active Weather Alerts dropdown using North Star colors, rounded alert rows, consistent spacing, and hover states.
- The top-right settings shortcut now opens Appearance directly.


## 1.1.15 — Alert ticker controls and polished alert popover
- Adds an Appearance checkbox controlling whether routine active weather alerts are included in the top ticker.
- Severe weather alerts still take ticker priority even if routine weather-alert ticker display is disabled.
- Keeps the configured normal ticker message in the same stream as weather-alert text.
- Renders only one ticker message instance at a time, eliminating repeated copies across wide displays.
- Replaces the alert popup with a rounded transparent JWindow to remove native white popup corners.
- Clicking the alert badge a second time closes the open alert panel.
- The alert panel also closes when focus moves elsewhere and retains North Star hover/detail behavior.


## 1.1.16 — Workspace Setup polish and functional width balancing
- Restores Main Dashboard Width Balance behavior; Save & Apply now changes the Main Showcase share and redistributes remaining width across side modules.
- Rebuilds Information Row Display and Operations Snapshot Display using the same spacing, control sizes, labels, and ticker terminology.
- Expands Movement controls so Continuous Ticker is readable instead of visually cramped.
- Renames the selector area to Information Cards.
- Replaces the long one-column block list with a two-column numbered card picker and larger dropdown controls.
- Clarifies Workspace Setup copy so a new administrator can distinguish visibility, card selection, movement, paging, and ticker speed.


## 1.4.1 — Smoother grid editing and unified location management
- Smooths dashboard tile movement/resizing by moving continuously with the mouse and snapping only on release.
- Adds a live snap-preview outline so the final grid destination is visible without tile jumping.
- Improves minimum-size and canvas-edge handling, especially for bottom-edge and corner resizing.
- Combines Traffic & Routes and Pinned Locations into one Locations & Routes navigation item.
- Replaces the two separate Settings pages with one unified Locations & Routes workspace.
- Presents Primary Facility, Pinned Locations and Traffic Routes with consistent rounded cards, spacing and controls.
- Supports creating a route directly from the selected pinned location.
- Preserves existing route and pinned-location data and authorization checks.


## 1.4.2 — Polished authentication and workspace startup transition
- Vertically centers the secure login content inside its dedicated lower startup region.
- Keeps the approved North Star loading animation at its original aspect ratio while reserving a balanced authentication area.
- Adds a branded post-login loading state with an indeterminate progress animation and workspace-preparation messaging.
- Keeps the completed startup artwork visible while the Operations Workspace is being constructed.
- Delays removal of the startup surface until the main workspace frame is visible, preventing the blank white/desktop flash during startup.
- Gives the Operations Workspace a North Star dark background before its first native paint for a cleaner macOS transition.


## 1.5.1 — IBM/DVIEW tracking extract + Truck Tracking UI polish
- Corrects Truck Tracking Settings control height/padding so spinner/combo values are fully visible.
- Adds explicit PRO number, trailer number, outbound shipment ID, customer/dealer key, source system, Ship IDs, and shipped date fields to the shipment model.
- Adds automatic detection/import of IBM/DVIEW `TrailerInfoFromTrackingNumber` CSV extracts.
- Consolidates line-level IBM rows by `OUTBOUNDSHIPMENTID`, preventing one outbound shipment from appearing dozens of times because it contains multiple Ship IDs.
- Preserves all unique Ship IDs and multiple FedEx tracking numbers while retaining a separate FedEx PRO number field.
- Uses deterministic IBM outbound shipment IDs so re-imports update existing records instead of generating duplicates.
- Preserves provider-enriched ETA/GPS/delay/status data when an IBM reference extract is re-imported.
- Automatically recognizes records containing a tracking number or PRO number as FedEx candidates; records without a confirmed carrier remain `OTHER` instead of being incorrectly labelled Penske.
- Archives old IBM reference records by default so historical extracts remain searchable without flooding the live Current view.
- Expands Current/History tables and manual edit forms to expose the new shipment identifiers.
\n\n## 1.5.2 — Historical truck playback and settings polish
- Corrects Truck Tracking numeric/spinner vertical spacing and text clipping.
- Adds a Playback / Test tab for using old delivered shipments as temporary in-transit map records.
- Playback can select any historical shipment, choose a configured route, scrub 0–100%, and play/pause the marker.
- Historical playback never mutates or reopens the stored shipment; the overlay is explicitly marked TEST on the map.
- Adds immediate map repaint notifications while scrubbing playback.
- Establishes the playback contract that future FedEx/Penske scan-event coordinates can use for true event-by-event historical replay.\n
## 1.5.3 — Historical roadway playback
- Adds TomTom roadway geometry reconstruction for old truck shipments.
- Adds Build Playback Route, Play/Pause, Reset, and Clear controls.
- Playback movement is distance-based for smoother road-following animation.
- Draws the reconstructed roadway and endpoint checkpoints on the main map.
- Clearly labels roadway reconstruction vs straight-line fallback.
- Preserves historical shipment data without modifying delivered status.
- Corrects clipped numeric controls in Truck Tracking Settings.
