from pathlib import Path

src=Path('.github/workflows/build.yml')
out=Path('ci/build-next.yml')
s=src.read_text()

old="javac --release 21 -encoding UTF-8 -d out $(find src -name '*.java')"
new="javac --release 21 -Xlint:unchecked -Werror -encoding UTF-8 -cp 'lib/*' -d out $(find src -name '*.java')"
if s.count(old)!=1:
    raise SystemExit('loop1 compile anchor mismatch')
s=s.replace(old,new,1)

old="javac --release 21 -encoding UTF-8 -d out-second $(find src -name '*.java')"
new="javac --release 21 -Xlint:unchecked -Werror -encoding UTF-8 -cp 'lib/*' -d out-second $(find src -name '*.java')"
if s.count(old)!=1:
    raise SystemExit('loop2 compile anchor mismatch')
s=s.replace(old,new,1)

anchor="          if [ -d resources ]; then cp -R resources/. out-second/; fi\n"
extraction=(
    "          for dep in lib/flatlaf-3.7.2.jar lib/flatlaf-intellij-themes-3.7.2.jar; do\n"
    "            (cd out-second && jar --extract --file \"../$dep\")\n"
    "          done\n"
    "          rm -f out-second/META-INF/MANIFEST.MF\n"
)
if s.count(anchor)!=1:
    raise SystemExit('loop2 resources anchor mismatch')
s=s.replace(anchor,extraction+anchor,1)

entry="            com/wtm/app/AiEnabledMain.class \\\n"
addition="            com/formdev/flatlaf/FlatLaf.class \\\n"
if s.count(entry)!=1:
    raise SystemExit('jar entry anchor mismatch')
s=s.replace(entry,entry+addition,1)

policy="          grep -q '^Implementation-Version: 2.1.34-clean-sweep$' MANIFEST.MF\n"
deps=(
    "          test -f lib/flatlaf-3.7.2.jar\n"
    "          test -f lib/flatlaf-intellij-themes-3.7.2.jar\n"
    "          test -f src/com/wtm/ui/ThemeManager.java\n"
)
if s.count(policy)!=1:
    raise SystemExit('policy anchor mismatch')
s=s.replace(policy,policy+deps,1)

out.write_text(s)
