from pathlib import Path

path = Path("build.sh")
source = path.read_text(encoding="utf-8")
anchor = '''java -Djava.awt.headless=true -cp '/tmp/ns-theme-smoke:out:lib/*' ThemeSmokeTest


# Runtime branding is mandatory.'''
replacement = '''java -Djava.awt.headless=true -cp '/tmp/ns-theme-smoke:out:lib/*' ThemeSmokeTest

# Verify that the packaged NorthStar company glyph atlas can actually be sliced
# and rendered. This catches mismatches between atlas cell dimensions and the
# dashboard renderer before a GUI candidate is published.
rm -rf /tmp/ns-glyph-smoke
mkdir -p /tmp/ns-glyph-smoke
javac --release 21 -Xlint:unchecked -Werror -encoding UTF-8 -cp 'out:lib/*' -d /tmp/ns-glyph-smoke ci/GlyphSmokeTest.java
java -Djava.awt.headless=true -cp '/tmp/ns-glyph-smoke:out:lib/*' GlyphSmokeTest

# Runtime branding is mandatory.'''
if anchor not in source:
    raise SystemExit("build glyph smoke insertion anchor not found")
path.write_text(source.replace(anchor, replacement, 1), encoding="utf-8")
