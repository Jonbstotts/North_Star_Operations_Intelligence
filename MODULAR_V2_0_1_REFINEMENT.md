# NorthStar Modular v2.0.1 Refinement

This refinement was built from the modular v2.0.0 compatibility project after UI testing on 2026-08-25.

## Shared UI corrections
- Replaced LAF-dependent internal tab state painting with a self-painted canonical NorthStar tab surface.
- Selected tabs use a rounded accent surface/border; unselected tabs no longer retain stale selection outlines.
- Enabled tab text is painted from the theme foreground directly to prevent grey/white flashing during theme refreshes.
- Locations title/description remain above the module tabs; Back to Dashboard is aligned on the same header row.
- Removed the opaque card-host artifact that could render as a white strip inside Logistics Network.
- Map Display is compact and top-aligned instead of stretching checkbox rows across the viewport.

## Logistics Network
- Returned to a clean NorthStar network import contract rather than using the raw DVIEW address table as the canonical template.
- Canonical columns: `Name, Location Type, Address, Network ID, Facing LC, Latitude, Longitude, Geofence Meters, Active, Notes`.
- `Location Type` explicitly supports Dealer, Logistics Center, Home Office, Supplier, Carrier Terminal, Cross-Dock / Consolidation Point, Service / Support Facility, and other operational destinations.
- Duplicate-safe upsert uses Location Type + Network ID, or normalized address when no Network ID is supplied.
- Added multi-select, Select All, Delete Selected, and Delete All network-management actions.
- Added imported-network Primary Location selection while retaining Manual / Custom entry.
- Added Home Office as a first-class pin-style category.

## Employees
- Removed the duplicate inner `Employee Operations` heading.
- Simplified the visible employee selector to Name + Active only while retaining the complete underlying employee record/model.

## Verification
- Clean Java 21 compile.
- Modular core/module tests passed (19 unique module IDs/routes and 19 concrete modules).
- Network importer regression passed: initial insert, zero-change identical re-import, and single-record changed-name upsert.
- Employee selector regression passed.
- Canonical tab selection/paint regression passed.
- Final JAR archive integrity passed.
- Startup smoke test passed with no linkage/resource/startup exceptions before controlled termination.
- OAuth client secret was confirmed absent from the packaged JAR.

The complete v2.0.1 source project and runnable JAR were produced as external build artifacts for local validation before merging the modular migration branch.