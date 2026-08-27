#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

if find src/com/wtm/app -maxdepth 1 -name 'NorthStarMain18*.java' -print -quit | grep -q .; then
  echo "ERROR: legacy NorthStarMain18xx launcher wrapper found. Use NorthStarMainStable only." >&2
  exit 1
fi

if ! grep -q '^Main-Class: com.wtm.app.NorthStarMainStable$' MANIFEST.MF; then
  echo "ERROR: MANIFEST.MF is not pointing at the canonical NorthStarMainStable launcher." >&2
  exit 1
fi

rm -rf out
mkdir -p out
javac --release 21 -encoding UTF-8 -d out $(find src -name '*.java')
if [ -d resources ]; then cp -R resources/. out/; fi
jar --create --file NorthStarOperations.jar --manifest MANIFEST.MF -C out .

DUPLICATES=$(zipinfo -1 NorthStarOperations.jar | sort | uniq -d | wc -l | tr -d ' ')
if [ "$DUPLICATES" != "0" ]; then
  echo "ERROR: built JAR contains duplicate archive entries." >&2
  exit 1
fi

unzip -tq NorthStarOperations.jar >/dev/null
echo "Built NorthStarOperations.jar from canonical src tree"
