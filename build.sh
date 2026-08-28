#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

# The master branch is the only canonical release source. Recovery/migration
# branches are retained for history and source reconciliation only; they must
# never be used accidentally to produce a release build.
if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  BRANCH=$(git symbolic-ref --quiet --short HEAD 2>/dev/null || true)
  if [ -n "$BRANCH" ] && [ "$BRANCH" != "master" ] && [ "${NORTHSTAR_ALLOW_NONCANONICAL_BRANCH:-0}" != "1" ]; then
    echo "ERROR: refusing release build from non-canonical branch '$BRANCH'. Checkout master first." >&2
    echo "Set NORTHSTAR_ALLOW_NONCANONICAL_BRANCH=1 only for deliberate development testing." >&2
    exit 1
  fi
fi

# No duplicate top-level Java source trees may compete with src/. Historical
# copies live under legacy_source_snapshot/ and are intentionally excluded.
LEGACY_ROOTS=(alerts callin config employee importer location map media model net radar security service sports traffic truck ui usage util weather)
for d in "${LEGACY_ROOTS[@]}"; do
  if [ -d "$d" ] && find "$d" -name '*.java' -print -quit | grep -q .; then
    echo "ERROR: legacy Java source tree '$d/' exists at repository root. Move it under legacy_source_snapshot/." >&2
    exit 1
  fi
done

if find src/com/wtm/app -maxdepth 1 -name 'NorthStarMain18*.java' -print -quit | grep -q .; then
  echo "ERROR: legacy NorthStarMain18xx launcher wrapper found. Use NorthStarMainStable only." >&2
  exit 1
fi

if ! grep -q '^Main-Class: com.wtm.app.NorthStarMainStable$' MANIFEST.MF; then
  echo "ERROR: MANIFEST.MF is not pointing at the canonical NorthStarMainStable launcher." >&2
  exit 1
fi

if [ ! -f src/com/wtm/modular/ui/WorkspaceLifecycleV3.java ] || \
   ! grep -q 'WorkspaceLifecycleV3' src/com/wtm/app/NorthStarMainStable.java; then
  echo "ERROR: consolidated WorkspaceLifecycleV3 is missing from the canonical startup path." >&2
  exit 1
fi

# Retired overlapping structural guards must stay out of the launcher. Their
# source may remain temporarily for history/reconciliation, but installing them
# reintroduces competing Dashboard/sidebar rebuild lifecycles.
for retired in WorkspaceUiRecoveryGuard MusicWorkspacePolishGuard StartupDashboardReadyGuard; do
  if grep -q "${retired}.*install" src/com/wtm/app/NorthStarMainStable.java; then
    echo "ERROR: retired lifecycle guard '$retired' is installed by NorthStarMainStable." >&2
    exit 1
  fi
done
if grep -q 'MusicModuleGuard.*install' src/com/wtm/app/NorthStarMainStable.java; then
  echo "ERROR: MusicModuleGuard.install must not be called; WorkspaceLifecycleV3 owns provider lifecycle boundaries." >&2
  exit 1
fi

# Dynamic workspace integrations must use the canonical source-backed extension
# API instead of reflecting into OperationsWorkspaceFrame private implementation
# details. Keep these checks in build.sh so local release builds and CI enforce
# the same architecture.
for file in \
  src/com/wtm/app/AiEnabledMain.java \
  src/com/wtm/modular/ui/MusicModuleGuard.java \
  src/com/wtm/modular/ui/WorkspaceLifecycleV3.java; do
  if grep -qE 'java\.lang\.reflect|getDeclaredField|getDeclaredMethod|setAccessible\(' "$file"; then
    echo "ERROR: private workspace reflection reintroduced in $file." >&2
    exit 1
  fi
done

for api in \
  registerWorkspaceExtensionRoute \
  showWorkspaceExtensionRoute \
  mountDashboardExtension \
  activeWorkspaceRouteName \
  workspaceConfigForExtensions; do
  if ! grep -q "$api" src/com/wtm/ui/OperationsWorkspaceFrame.java; then
    echo "ERROR: canonical workspace extension API '$api' is missing." >&2
    exit 1
  fi
done

if ! grep -q 'workspaceConfigForExtensions' src/com/wtm/ui/SettingsDialog.java; then
  echo "ERROR: SettingsDialog source-backed config accessor is missing." >&2
  exit 1
fi

if grep -qE 'COMPONENT_ADDED|CONTAINER_EVENT_MASK|ContainerEvent' src/com/wtm/modular/ui/WorkspaceLifecycleV3.java; then
  echo "ERROR: generic component-add lifecycle handling returned to WorkspaceLifecycleV3." >&2
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

echo "Built NorthStarOperations.jar from canonical master src tree"
