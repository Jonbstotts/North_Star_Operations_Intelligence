# North Star Transportation Tracking — v1.6.0

## FedEx
FedEx Basic Integrated Visibility can now populate persistent scan events for
tracking numbers imported from DVIEW. North Star preserves event time, status,
carrier-reported place, geocoded coordinates when needed, ETA, and delivery.

The map/playback differentiates carrier checkpoints from road reconstruction.
Basic FedEx visibility is event-based and is not presented as continuous vehicle
GPS.

## Trak-4
North Star now owns a reusable GPS layer independent of Penske:

Trak-4 device -> physical trailer -> today's DVIEW Trailer ID ->
OutboundShipmentID -> shipment/load

Assignments are historical. Reassigning a tracker closes the prior assignment
rather than overwriting it.

Trak-4 publicly supports REST GPS reports and webhooks. North Star includes a
REST adapter with configurable GPS Reports URL and authorization header/token
because those exact values are account/API-version specific.
