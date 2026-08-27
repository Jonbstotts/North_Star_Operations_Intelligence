# NorthStar Canonical Source Policy

## Release source of truth

`master` is the only canonical release branch for NorthStar Operations Intelligence.

Release builds must use `MANIFEST.MF`, `build.sh`, and the `src/` tree from `master`. The build script refuses release builds from another named branch unless `NORTHSTAR_ALLOW_NONCANONICAL_BRANCH=1` is deliberately supplied for development testing.

## Recovery and migration branches

`migration/modular-core-v2` and `recovery/v1.6.3` are retained as historical/reconciliation branches. They are intentionally **not** merged wholesale into `master` because they diverged before the current UI stabilization work and contain legacy launcher wrappers and older UI implementations that can reintroduce regressions.

When source exists only on one of these branches, reconcile that file selectively into `master` after comparing it against the current runtime behavior. Do not replace current `master` files with an older branch version simply because the older branch contains more files.

## Workspace UI extensions

Dynamic workspace extensions must survive a Settings `Save & Apply` rebuild. `WorkspaceUiRecoveryGuard` is part of the canonical startup path and is responsible for restoring dynamic sidebar routes and NorthStar Intelligence after the workspace content tree is replaced.

The guard also supplies the NorthStar Intelligence dashboard visibility checkbox in Workspace Setup and preserves that selection independently of Main Showcase announcement settings.

## Legacy launchers

`NorthStarMainStable` is the only supported launcher. `build.sh` rejects the old `NorthStarMain18xx` wrapper chain if any such file is reintroduced into the canonical `src/com/wtm/app` tree.
