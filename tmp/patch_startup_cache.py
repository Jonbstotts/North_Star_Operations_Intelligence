from pathlib import Path

settings = Path('src/com/wtm/ui/SettingsDialog.java')
text = settings.read_text()
old = 'Path imported=MediaService.importStartupVideo(chooser.getSelectedFile().toPath());'
new = 'Path imported=StartupMediaService.importVideo(chooser.getSelectedFile().toPath());'
if old not in text:
    raise SystemExit('startup import call target not found')
text = text.replace(old, new, 1)

old_help = "\n".join([
    '                "<html>The intro is copied into North Star managed storage instead of the application JAR. "',
    '              + "H.264 MP4/MOV is recommended. Press <b>Esc</b> or <b>Skip Intro</b> during playback. "',
    '              + "This first portable implementation plays the visual track without audio.</html>"));',
])
new_help = "\n".join([
    '                "<html>The intro is copied into North Star managed storage instead of the application JAR. "',
    '              + "Its final decodable frame is cached losslessly and becomes the stationary login artwork "',
    '              + "for normal completion, <b>Skip Intro</b>, and startup with the intro disabled. "',
    '              + "For smooth portable playback, <b>1280×720 H.264 MP4 at 30 FPS constant frame rate</b> is recommended. "',
    '              + "Press <b>Esc</b> or <b>Skip Intro</b> during playback. The visual track plays without audio.</html>"));',
])
if old_help not in text:
    raise SystemExit('startup help target not found')
text = text.replace(old_help, new_help, 1)
settings.write_text(text)

build = Path('build.sh')
text = build.read_text()
old_guard = "\n".join([
    'if [ ! -f src/com/wtm/ui/StartupExperienceManager.java ] || \\',
    '   [ ! -f src/com/wtm/ui/StartupPresentationLayout.java ] || \\',
    '   [ ! -f src/com/wtm/ui/StartupLoginDialog.java ] || \\',
    '   [ ! -f src/com/wtm/ui/LoginFormPanel.java ] || \\',
    "   ! grep -Fq 'peekStartupExperience' src/com/wtm/config/ConfigService.java || \\",
    "   ! grep -Fq 'StartupLoginDialog.authenticate' src/com/wtm/app/Main.java; then",
    '  echo "ERROR: canonical startup presentation/login ownership is missing." >&2',
    '  exit 1',
    'fi',
    '',
])
new_guard = "\n".join([
    'if [ ! -f src/com/wtm/ui/StartupExperienceManager.java ] || \\',
    '   [ ! -f src/com/wtm/ui/StartupPresentationLayout.java ] || \\',
    '   [ ! -f src/com/wtm/ui/StartupLoginDialog.java ] || \\',
    '   [ ! -f src/com/wtm/ui/LoginFormPanel.java ] || \\',
    '   [ ! -f src/com/wtm/media/StartupMediaService.java ] || \\',
    "   ! grep -Fq 'peekStartupExperience' src/com/wtm/config/ConfigService.java || \\",
    "   ! grep -Fq 'StartupLoginDialog.authenticate' src/com/wtm/app/Main.java || \\",
    "   ! grep -Fq 'StartupMediaService.posterFor(video)' src/com/wtm/ui/StartupExperienceManager.java || \\",
    "   ! grep -Fq 'StartupMediaService.importVideo' src/com/wtm/ui/SettingsDialog.java; then",
    '  echo "ERROR: canonical startup presentation/media ownership is missing." >&2',
    '  exit 1',
    'fi',
    "if grep -Fq 'MediaService.importStartupVideo' src/com/wtm/ui/SettingsDialog.java; then",
    '  echo "ERROR: Settings bypassed StartupMediaService final-frame validation/cache ownership." >&2',
    '  exit 1',
    'fi',
    '',
])
if old_guard not in text:
    raise SystemExit('startup ownership build guard target not found')
text = text.replace(old_guard, new_guard, 1)

old_compile = "\n".join([
    '  ci/BasemapProviderSmokeTest.java \\',
    '  ci/StartupPresentationLayoutSmokeTest.java',
])
new_compile = "\n".join([
    '  ci/BasemapProviderSmokeTest.java \\',
    '  ci/StartupPresentationLayoutSmokeTest.java \\',
    '  ci/StartupMediaServiceSmokeTest.java',
])
if old_compile not in text:
    raise SystemExit('foundation compile list target not found')
text = text.replace(old_compile, new_compile, 1)

old_run = "java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' StartupPresentationLayoutSmokeTest\n"
new_run = old_run + "java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' StartupMediaServiceSmokeTest\n"
if old_run not in text:
    raise SystemExit('startup layout smoke run target not found')
text = text.replace(old_run, new_run, 1)
build.write_text(text)
