# NorthStar Canonical Source Policy

## Release source of truth

`master` is the only canonical development and release-source branch for NorthStar Operations Intelligence.

All new source changes, manifests, lifecycle code, provider integrations, and release metadata must land on `master`. Recovery and migration branches are historical inputs only and must never be merged wholesale into a release.

The only compilable Java source root is `src/`. IntelliJ and `build.sh` both point at that tree.

## Archived legacy source

Older duplicate top-level Java source trees have been moved under `legacy_source_snapshot/` so they remain available for history and selective reconciliation without competing with the canonical `src/` tree.

Files under `legacy_source_snapshot/` are **not release source** and must never be added to an IDE source root or passed to `javac`. If a useful implementation exists only in the archive, reconcile it deliberately into `src/` after comparing it with the current runtime behavior.

A pre-cleanup safety branch, `archive/pre-clean-sweep-2026-08-27`, preserves the repository state from immediately before this source-root cleanup.

## Verified runtime baseline during reconciliation

The stabilized runtime remains a binary compatibility reference while any remaining runtime-only classes are reconciled into canonical Java source. Release packaging must never replace current stabilized behavior with an older migration/recovery branch implementation merely because that branch contains more files.

The GitHub Actions workflow performs clean `master` source-validation passes. Historical migration/recovery branches are not release inputs.

## Recovery and migration branches

`migration/modular-core-v2` and `recovery/v1.6.3` are retained for history and selective source reconciliation. They intentionally remain separate because they diverged before the current UI stabilization work and contain legacy launcher wrappers and older UI implementations.

Do not merge either branch wholesale into `master`.

## Workspace lifecycle ownership

`ModuleUiCoordinatorLifecycle` owns modular sidebar restoration and route adaptation at safe workspace boundaries.

`WorkspaceLifecycleV3` owns the remaining dynamic workspace additions that are not yet native to `OperationsWorkspaceFrame`: the NorthStar Intelligence Workspace Setup toggle, Music Compact Player toggle, Music & Audio route/dashboard synchronization, and the one deliberate Dashboard remount after first workspace activation or Settings `Save & Apply`.

The retired `WorkspaceUiRecoveryGuard`, `MusicWorkspacePolishGuard`, and `StartupDashboardReadyGuard` must not be installed by the canonical launcher. `MusicModuleGuard.install()` must also not be called because its historical global COMPONENT_ADDED listener performs structural injection. `WorkspaceLifecycleV3` invokes only its bounded provider/sidebar/dashboard methods at explicit lifecycle boundaries.

**Rule:** ordinary `COMPONENT_ADDED` events may perform cosmetic normalization only. They must never trigger a workspace rebuild, sidebar reinstall, Dashboard route change, or module coordinator reinstall.

`SidebarModulesGlyphGuard`, `TrustedEmailUiGuard`, and `DashboardGlyphArtGuard` remain cosmetic/configuration startup services.

## Music & Audio

The first Music & Audio provider is Apple Music through MusicKit. NorthStar does not download/store music or collect Apple ID passwords. Playback is delegated to Apple Music through a loopback-only control bridge, while playlist editing remains in Apple Music.

Music settings are local configuration and must be stored with owner-only permissions where supported.

## Secrets and local security

API credentials must use `ApiCredentialService`/`SecureFiles` rather than ordinary configuration. OAuth/token files and any provider credential files must live in private application-data directories and use owner-only file permissions where the operating system supports them.

Gmail DataPath document ingestion is fail-closed: at least one exact trusted sender must be configured before attachments are queried/imported.

## Legacy launchers

`NorthStarMainStable` is the only supported launcher. Numbered `NorthStarMain18xx` launcher wrappers must not be restored to the canonical `src/com/wtm/app` tree.
