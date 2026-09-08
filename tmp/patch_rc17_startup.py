from pathlib import Path

build=Path('build.sh')
text=build.read_text()

old="   ! grep -Fq 'StartupMediaService.posterFor(video)' src/com/wtm/ui/StartupExperienceManager.java || \\\n"
new="   ! grep -Fq 'StartupMediaService.cachedPosterFor(video)' src/com/wtm/ui/StartupExperienceManager.java || \\\n"
if old not in text:
    raise SystemExit('startup poster build guard target not found')
text=text.replace(old,new,1)

needle='''if grep -Fq 'MediaService.importStartupVideo' src/com/wtm/ui/SettingsDialog.java; then
  echo "ERROR: Settings bypassed StartupMediaService final-frame validation/cache ownership." >&2
  exit 1
fi
'''
replacement=needle+'''if grep -Fq 'seekToFramePrecise' src/com/wtm/media/StartupMediaService.java || \\
   grep -Fq 'seekToSecondPrecise' src/com/wtm/media/StartupMediaService.java || \\
   ! grep -Fq 'startPosterMigration(video)' src/com/wtm/ui/StartupExperienceManager.java; then
  echo "ERROR: startup resting-frame migration can block or regress launch responsiveness." >&2
  exit 1
fi
'''
if needle not in text:
    raise SystemExit('startup media guard insertion target not found')
text=text.replace(needle,replacement,1)

old_compile='''  ci/StartupPresentationLayoutSmokeTest.java \\
  ci/StartupMediaServiceSmokeTest.java
'''
new_compile='''  ci/StartupPresentationLayoutSmokeTest.java \\
  ci/StartupMediaServiceSmokeTest.java \\
  ci/StartupLaunchResponsivenessSmokeTest.java
'''
if old_compile not in text:
    raise SystemExit('startup smoke compile list target not found')
text=text.replace(old_compile,new_compile,1)

old_run="java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' StartupMediaServiceSmokeTest\n"
new_run=old_run+"java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' StartupLaunchResponsivenessSmokeTest\n"
if old_run not in text:
    raise SystemExit('startup smoke run target not found')
text=text.replace(old_run,new_run,1)

build.write_text(text)
