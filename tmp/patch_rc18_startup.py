from pathlib import Path

# Repair the ZipFile stream's wildcard capture before compiling. Keep this in
# the temporary validator so the branch can prove the source fix before commit.
cache=Path('src/com/wtm/media/StartupPlaybackCacheService.java')
cache_text=cache.read_text()
old_cache='''            List<ZipEntry> frames=zip.stream()
                    .filter(entry->!entry.isDirectory()&&entry.getName().startsWith(FRAME_PREFIX))
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .toList();
'''
new_cache='''            List<ZipEntry> frames=zip.stream()
                    .map(ZipEntry.class::cast)
                    .filter(entry->!entry.isDirectory()&&entry.getName().startsWith(FRAME_PREFIX))
                    .sorted(Comparator.comparing(ZipEntry::getName))
                    .toList();
'''
if old_cache in cache_text:
    cache_text=cache_text.replace(old_cache,new_cache,1)
elif new_cache not in cache_text:
    raise SystemExit('startup playback cache reader target not found')
cache.write_text(cache_text)

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
if old in text:
    text=text.replace(old,new,1)
elif new not in text:
    raise SystemExit('startup required-file block not found')

needle='''if grep -Fq 'seekToFramePrecise' src/com/wtm/media/StartupMediaService.java || \\
   grep -Fq 'seekToSecondPrecise' src/com/wtm/media/StartupMediaService.java || \\
   ! grep -Fq 'startPosterMigration(video)' src/com/wtm/ui/StartupExperienceManager.java; then
  echo "ERROR: startup resting-frame migration can block or regress launch responsiveness." >&2
  exit 1
fi
'''
extra='''if ! grep -Fq 'StartupPlaybackCacheService.openFresh' src/com/wtm/ui/StartupExperienceManager.java || \\
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
if extra not in text:
    if needle not in text:
        raise SystemExit('startup gate insertion point not found')
    text=text.replace(needle,needle+extra,1)

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
if old_compile in text:
    text=text.replace(old_compile,new_compile,1)
elif new_compile not in text:
    raise SystemExit('foundation smoke compile list not found')

old_run="java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' StartupLaunchResponsivenessSmokeTest\n"
extra_run="java -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' StartupPlaybackCacheSmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' StartupTransitionPolicySmokeTest\n"
if extra_run not in text:
    if old_run not in text:
        raise SystemExit('foundation smoke run list not found')
    text=text.replace(old_run,old_run+extra_run,1)

build.write_text(text)
