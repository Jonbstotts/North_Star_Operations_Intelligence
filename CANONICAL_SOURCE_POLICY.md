# NorthStar Canonical Source Policy

## Release source of truth

`master` is the only canonical development and release-source branch for NorthStar Operations Intelligence.

All new source changes, manifests, lifecycle guards, provider integrations, and release metadata must land on `master`. Recovery and migration branches are historical inputs only and must never be merged wholesale into a release.

## Verified runtime baseline during reconciliation

The stabilized v2.1.29 runtime is the current binary compatibility baseline while the remaining runtime-only classes are reconciled back into canonical Java source. A release must preserve that verified runtime baseline and overlay only intentionally reviewed current changes until source reconciliation is complete.

This is deliberately safer than rebuilding a runnable application from `migration/modular-core-v2`: that historical runtime predates multiple UI, NorthStar Intelligence, Data Collection, Modules, Truck Tracking, startup, and dashboard stabilization fixes. Using it as a release base can silently reintroduce old behavior.

The GitHub Actions workflow therefore performs two clean `master` source-validation passes but intentionally does **not** publish a runnable release JAR from the migration runtime. This prevents an incomplete historical runtime from being mistaken for the current product.

## Recovery and migration branches

`migration/modular-core-v2` and `recovery/v1.6.3` are retained for history and selective source reconciliation. They intentionally remain separate because they diverged before the current UI stabilization work and contain legacy launcher wrappers and older UI implementations.

When a still-active source file exists only on one of these branches, reconcile that file selectively into `master` after comparing it against the verified current runtime behavior. Do not replace a current file with an older branch version merely because the historical branch contains more files.

## Workspace UI extensions

Dynamic workspace extensions must survive a Settings `Save & Apply` rebuild. `WorkspaceUiRecoveryGuard` remains part of the canonical startup path and is responsible for restoring dynamic sidebar routes and NorthStar Intelligence after the workspace content tree is replaced.

The guard also supplies the NorthStar Intelligence dashboard visibility checkbox in Workspace Setup and preserves that selection independently of Main Showcase announcement settings.

`SidebarModulesGlyphGuard`, `TrustedEmailUiGuard`, `DashboardGlyphArtGuard`, and the Music & Audio provider module remain explicit startup services. Changes to these lifecycle components must not reintroduce broad structural rebuilding on ordinary module navigation.

## Music & Audio

The first Music & Audio provider is Apple Music through MusicKit. NorthStar does not store music files or Apple ID passwords. Playback is delegated to Apple Music through the local loopback control bridge, while playlist editing remains in the account holder's Apple Music service.

## Legacy launchers

`NorthStarMainStable` is the only supported launcher. Numbered `NorthStarMain18xx` launcher wrappers must not be restored to the canonical `src/com/wtm/app` tree.
