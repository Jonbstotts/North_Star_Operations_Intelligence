# Truck Tracking Historical Road Playback — v1.5.3

North Star can now reconstruct an old shipment on actual road geometry.

## Playback modes
- **Reconstructed roadway**: TomTom calculates the roadway between the selected
  known endpoints and the test truck follows that geometry.
- **Straight-line fallback**: retained only if road routing is unavailable.

Playback is non-destructive: selecting a delivered shipment never changes its
stored status or history.

The reconstructed road is a probable route. It is not represented as an exact
FedEx/Penske GPS trail unless carrier-provided location events are available.
The engine is checkpoint-oriented so future carrier scan/GPS events can become
anchors for the same playback system.
