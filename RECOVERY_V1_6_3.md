# North Star v1.6.3 Recovery Baseline

This branch exists to reconstruct the exact editable source corresponding as closely as possible to the surviving North Star Operations Intelligence v1.6.3 runnable JAR.

## Evidence hierarchy

1. Surviving v1.6.3 JAR (runtime behavior / compiled API)
2. Existing GitHub source and resources
3. CHANGELOG and architecture documentation
4. Reconstructed source only where the JAR contains newer implementation than GitHub

## Verified v1.6.3 JAR identity

- Main class: `com.wtm.app.Main`
- Implementation title: `North Star Operations Intelligence`
- Implementation version: `1.6.3`
- Java runtime/build family: Java 21

## Confirmed recovery gap

The v1.6.3 JAR contains startup-media classes that are not currently present in the GitHub source index, including:

- `com.wtm.ui.StartupMediaService`
- `StartupMediaService.Slot`
- `StartupMediaService.FrameSource`
- `StartupMediaService.VideoInstallResult`
- `StartupMediaService.VideoMetadata`
- `StartupMediaService.LazyFileFrames`

The compiled v1.6.3 API confirms support for:

- external startup animation frames
- three startup-media slots
- custom animation detection and summaries
- configurable frame delay
- direct video-file installation
- video metadata probing
- animation ZIP installation
- per-slot audio installation/playback
- clearing/resetting animation and audio

The v1.6.3 JAR also contains the newer `NorthStarSplashScreen`, `SettingsDialog`, and `Main` classes that integrate this startup-media system.

## Recovery rule

Do not merge this branch into `master` until the reconstructed source compiles cleanly and the resulting application is tested against the known-good v1.6.3 JAR.

After recovery, GitHub becomes the authoritative North Star source and future release JARs should be built from tagged/committed source rather than treated as the only surviving copy.
