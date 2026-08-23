# North Star Truck Tracking

## What works in v1.5.0

- Dedicated Truck Tracking sidebar workspace.
- Current, History, Import / Export, and Settings tabs.
- Carrier-neutral records for FedEx Freight and Mercedes StarHub / Penske.
- Map markers when latitude/longitude are available.
- Independent FedEx and StarHub/Penske map visibility toggles.
- Delivered-map retention, Current-view history window, archive age, and long-term retention settings.
- CSV import/update, blank-template export, full-history export, archive-after-export, and permanent purge.
- Optional Operations Snapshot sources:
  - Inbound Trucks
  - Delayed Trucks
  - Weather Delays
  - Traffic Delays
  - Next Truck ETA
- FedEx and Penske credentials use the encrypted North Star credential store.

## FedEx

Set the provider mode to `MANUAL` while using PRO-number CSV/manual records.

`FEDEX_REST` is included as the production integration mode and the encrypted
client ID/secret fields are ready for a FedEx Developer project. The application
does not fabricate live GPS coordinates: map markers are shown only when a
provider/import supplies coordinates.

## Penske / StarHub

Use `MANUAL_CSV` until the Mercedes/Penske account interface is identified.
`ENTERPRISE_FEED` reserves an HTTPS endpoint and encrypted token for the
customer-specific machine-to-machine interface. This can later be connected
without changing the shipment/history/map architecture.

## CSV natural-key behavior

Imports update an existing record when either:

1. `id` matches an existing shipment, or
2. `carrier + trackingNumber` matches an existing shipment.

This allows daily refresh files to update current shipments without creating
duplicates.


## IBM / DVIEW TrailerInfoFromTrackingNumber extracts

v1.5.1 recognizes the following source columns directly:

- `SHIPPEDDATE`
- `CUSTOMERBK`
- `TRAILER`
- `SHIPIDBK`
- `TRACKINGNUMBER`
- `PRONUMBER`
- `OUTBOUNDSHIPMENTID`

The IBM extract is Ship-ID/line grain, not shipment grain. North Star groups
records by `OUTBOUNDSHIPMENTID`, retains all unique Ship IDs and tracking
numbers, and stores the trailer/customer/date reference on the consolidated
shipment.

The extract is also useful as a future integration bridge:
`OUTBOUNDSHIPMENTID` and `TRAILER` can be used to correlate Penske/StarHub
loads, while `TRACKINGNUMBER` and `PRONUMBER` can feed FedEx tracking.
