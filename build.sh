#!/bin/bash
set -e
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
javac --release 21 -encoding UTF-8 -d out $(find src -name '*.java')
if [ -d resources ]; then cp -R resources/. out/; fi
jar --create --file NorthStarOperations.jar --manifest MANIFEST.MF -C out .
echo "Built NorthStarOperations.jar"
