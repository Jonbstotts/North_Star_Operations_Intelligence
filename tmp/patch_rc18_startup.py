from pathlib import Path

build=Path('build.sh')
text=build.read_text()

old='''   [ ! -f src/com/wtm/ui/LoginFormPanel.java ] || \\
   [ ! -f src/com/wtm/media/StartupMediaService.java ] || \\
'''
new='''   [ ! -f src/com/wtm/ui/LoginFormPanel.java ] || \\
   [ ! -f src/com/wtm/ui/StartupTransitionPolicy.java ] || \\
   [ ! -f src/com/wtm/media/StartupMediaService.java ] || \\
   [ ! -f src/com/wtm/media/StartupPlaybackCacheService.java ] || \\
'''
if old not in text:
    raise SystemExit('startup required-file block not found')
text=text.replace(old,new,1)

needle='''if grep -Fq 'seekToFramePrecise' src/com/wtm/media/StartupMediaService.java || \\
   grep -Fq 'seekToSecondPrecise' src/com/wtm/media/StartupMediaService.java || \\
   ! grep -Fq 'startPosterMigration(video)' src/com/wtm/ui/StartupExperienceManager.java; then
  echo "ERROR: startup resting-frame migration can block or regress launch responsiveness." >&2
  exit 1
fi
'''
addition=needle+'''if ! grep -Fq 'StartupPlaybackCacheService.openFresh' src/com/wtm/ui/StartupExperienceManager.java || \\
   ! grep -Fq 'StartupPlaybackCacheService.ensure' src/com/wtm/ui/StartupExperienceManager.java || \\
   ! grep -Fq 'StartupTransitionPolicy.revealProgress' src/com/wtm/ui/StartupLoginDialog.java; then
  echo "ERROR: display-ready startup playback/reveal ownership is missing." >&2
  exit 1
fi
if ! grep -Fq 'Theme.setActive(AppTheme.NORTH_STAR.id())' src/com/wtm/app/Main.java || \\
   ! grep -Fq 'Theme.setActive(workspaceTheme.id())' src/com/wtm/app/Main.java || \\
   ! grep -Fq 'Theme.setActive(AppTheme.NORTH_STAR.id())' src/com/wtm/ui/StartupLoginDialog.java || \\
   grep -Fq 'Theme.setActive(requestedTheme' src/com/wtm/ui/StartupLoginDialog.java; then
  echo "ERROR: startup branding and saved workspace theme are no longer isolated." >&2
  exit 1
fi
'''
if needle not in text:
    raise SystemExit('startup gate insertion point not found')
text=text.replace(needle,addition,1)

old_compile='''  ci/StartupPresentationLayoutSmokeTest.java \\
  ci/StartupMediaServiceSmokeTest.java \\
  ci/StartupLaunchResponsivenessSmokeTest.java
'''
new_compile='''  ci/StartupPresentationLayoutSmokeTest.java \\
  ci/StartupMediaServiceSmokeTest.java \\
  ci/StartupLaunchResponsivenessSmokeTest.java \\
  ci/StartupPlaybackCacheSmokeTest.java \\
  ci/StartupTransitionPolicySmokeTest.java
'''
if old_compile not in text:
    raise SystemExit('foundation smoke compile list not found')
text=text.replace(old_compile,new_compile,1)

old_run="java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' StartupLaunchResponsivenessSmokeTest\n"
new_run=old_run+"java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' StartupPlaybackCacheSmokeTest\n"+"java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' StartupTransitionPolicySmokeTest\n"
if old_run not in text:
    raise SystemExit('foundation smoke run list not found')
text=text.replace(old_run,new_run,1)

build.write_text(text)
