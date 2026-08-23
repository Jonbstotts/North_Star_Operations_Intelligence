# North Star v1.6.4 recovery/update notes

This update was produced from the known-good v1.6.3 JAR while the recovered source tree is being normalized.

## Implemented

- Truck Tracking settings controls are normalized to 48 px so spinner/editor values are readable instead of vertically clipped.
- FedEx Freight PRO numbers are treated as the authoritative carrier identifier when present.
- FedEx Basic Integrated Visibility remains the tracking provider for both FedEx Freight and FedEx parcel/Express/Ground identifiers.
- Detailed FedEx scan events are requested with `includeDetailedScans=true` and retained as checkpoint history for map playback.
- FedEx service type is used as a fallback movement mode when individual scan events do not state one: Freight/Ground => GROUND; Express/Overnight/2Day => AIR.
- Empty FedEx API results now produce a useful configuration/environment error rather than appearing as a successful zero-result refresh.

## Recovery compatibility patch

The surviving v1.6.3 `TruckTrackingPanel.class` still read only `shipment.trackingNumber` when the user pressed **Refresh FedEx Events**. The v1.6.4 runnable JAR patches that call site to use `TruckShipment.primaryCarrierIdentifier()` so a Freight record can submit its PRO number. The equivalent source-level edit to `ui/TruckTrackingPanel.java` should be retained when that large recovered source file is normalized into the canonical source tree.

## Still to complete

True unattended FedEx polling should be moved into the normal Truck Tracking refresh lifecycle so REST-enabled shipments refresh on `truckRefreshMinutes` from application startup until delivery. The current recovered `OperationsWorkspaceFrame` refresh loop only handles Trak-4 plus retention housekeeping; it does not yet poll FedEx automatically.
