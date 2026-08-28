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

# Retired overlapping structural guards must stay out of the launcher.
for retired in WorkspaceUiRecoveryGuard MusicWorkspacePolishGuard StartupDashboardReadyGuard; do
  if grep -q "${retired}.*install" src/com/wtm/app/NorthStarMainStable.java; then
    echo "ERROR: retired lifecycle guard '$retired' is installed by NorthStarMainStable." >&2
    exit 1
  fi
done
if grep -q 'MusicWorkspaceModule.*install' src/com/wtm/app/NorthStarMainStable.java; then
  echo "ERROR: MusicWorkspaceModule.install must not be called; WorkspaceLifecycleV3 owns provider lifecycle boundaries." >&2
  exit 1
fi

# The old generic module registry/manager architecture was never a canonical
# source-backed feature. The live workspace owns concrete routes directly.
if [ -e src/com/wtm/modular/core/ModuleRegistry.java ] || [ -e src/com/wtm/modular/core/ModularBootstrap.java ]; then
  echo "ERROR: obsolete generic modular-core shell returned to canonical source." >&2
  exit 1
fi
if grep -qE 'ModuleRegistry|ModularBootstrap' src/com/wtm/app/NorthStarMainStable.java; then
  echo "ERROR: canonical launcher references retired generic modular-core infrastructure." >&2
  exit 1
fi

# Generic presentation compatibility shims have been retired. Feature layout
# belongs to canonical source components and dialog platform theming belongs to
# ThemeStyler directly.
if [ -e src/com/wtm/ui/UiFinalPolish.java ] || \
   [ -e src/com/wtm/ui/ThemeCoreV216.java ] || \
   [ -e src/com/wtm/ui/NorthStarThemeCompliance.java ]; then
  echo "ERROR: retired presentation compatibility shim returned to canonical source." >&2
  exit 1
fi
if grep -q 'ThemeCoreV216' src/com/wtm/ui/ThemeStyler.java || \
   ! grep -q 'apple.awt.windowAppearance' src/com/wtm/ui/ThemeStyler.java; then
  echo "ERROR: canonical dialog theme ownership is not fully contained in ThemeStyler." >&2
  exit 1
fi

# Sidebar route identity must be explicit at construction. ThemeStyler must not
# rediscover routes by implementation class names or button text.
if ! grep -Fq 'button.putClientProperty("northstar.sidebar.route",Boolean.TRUE)' src/com/wtm/ui/OperationsWorkspaceFrame.java; then
  echo "ERROR: canonical sidebar buttons are not explicitly marked as routes." >&2
  exit 1
fi
if grep -qiE 'getClass\(\)\.getName|OperationsWorkspaceFrame\$RoundedSidebarButton|data collection|northstar intelligence' src/com/wtm/ui/ThemeStyler.java; then
  echo "ERROR: ThemeStyler contains stale sidebar route discovery heuristics." >&2
  exit 1
fi

# Data Collection and Gmail trusted-sender management are source-owned by a
# concrete workspace panel. The old component-discovery guard and GlassSurface
# runtime target must not return.
if [ ! -f src/com/wtm/ui/DataCollectionPanel.java ] || \
   ! grep -q 'class DataCollectionPanel' src/com/wtm/ui/DataCollectionPanel.java || \
   ! grep -q 'TRUSTED EMAIL SENDERS' src/com/wtm/ui/DataCollectionPanel.java || \
   ! grep -q 'TrustedSenderPolicy.parse' src/com/wtm/ui/DataCollectionPanel.java || \
   ! grep -q 'sideDataCollectionButton' src/com/wtm/ui/OperationsWorkspaceFrame.java; then
  echo "ERROR: canonical Data Collection/Gmail management workspace is missing." >&2
  exit 1
fi
if [ -e src/com/wtm/modular/ui/TrustedEmailUiGuard.java ] || \
   grep -R -q --include='*.java' 'GlassSurfacePanel' src; then
  echo "ERROR: obsolete trusted-email component discovery returned to canonical source." >&2
  exit 1
fi

# The former global UI Foundation listener is retired. Initial workspace styling
# is applied synchronously by OperationsWorkspaceFrame, while dialogs and late
# feature surfaces theme themselves at construction/mount boundaries.
if [ -e src/com/wtm/ui/foundation/UiFoundationRuntime.java ] || \
   [ -e src/com/wtm/ui/foundation/UiFoundation.java ]; then
  echo "ERROR: retired global UI Foundation runtime returned to canonical source." >&2
  exit 1
fi
if grep -q 'UiFoundationRuntime' src/com/wtm/app/NorthStarMainStable.java || \
   ! grep -q 'ThemeStyler.apply(this,Theme.active())' src/com/wtm/ui/OperationsWorkspaceFrame.java; then
  echo "ERROR: canonical workspace theme ownership is not local to OperationsWorkspaceFrame." >&2
  exit 1
fi

# WorkspaceLifecycleV3 is an explicit coordinator, not a global event observer.
# The workspace and Settings source owners call it at concrete lifecycle points.
if grep -qE 'addAWTEventListener|AWTEvent|Window\.getWindows|WINDOW_ACTIVATED|WINDOW_OPENED|ACTION_EVENT_MASK|WeakHashMap|SwingUtilities\.invokeLater' \
    src/com/wtm/modular/ui/WorkspaceLifecycleV3.java; then
  echo "ERROR: global/delayed workspace lifecycle observation returned to WorkspaceLifecycleV3." >&2
  exit 1
fi
for boundary in \
  'WorkspaceLifecycleV3.initializeWorkspace(this)' \
  'WorkspaceLifecycleV3.dashboardMounted(this)'; do
  if ! grep -Fq "$boundary" src/com/wtm/ui/OperationsWorkspaceFrame.java; then
    echo "ERROR: explicit workspace lifecycle boundary '$boundary' is missing." >&2
    exit 1
  fi
done
if grep -Fq 'routeMounted' src/com/wtm/modular/ui/WorkspaceLifecycleV3.java || \
   grep -Fq 'WorkspaceLifecycleV3.routeMounted' src/com/wtm/ui/OperationsWorkspaceFrame.java; then
  echo "ERROR: obsolete generic route-mounted compatibility boundary returned." >&2
  exit 1
fi
for boundary in \
  'WorkspaceLifecycleV3.settingsReady(this)' \
  'WorkspaceLifecycleV3.persistSettings(this)' \
  'WorkspaceLifecycleV3.settingsApplied(this)'; do
  if ! grep -Fq "$boundary" src/com/wtm/ui/SettingsDialog.java; then
    echo "ERROR: explicit Settings lifecycle boundary '$boundary' is missing." >&2
    exit 1
  fi
done

# Optional Workspace Setup controls are registered through a source-backed API.
# WorkspaceLifecycleV3 must not rediscover the module grid by visible checkbox
# labels or component ancestry.
if ! grep -q 'registerWorkspaceModuleToggle' src/com/wtm/ui/SettingsDialog.java || \
   ! grep -q 'workspaceModuleToggle' src/com/wtm/ui/SettingsDialog.java || \
   ! grep -q 'workspaceModulesPanel' src/com/wtm/ui/SettingsDialog.java; then
  echo "ERROR: source-backed Workspace Setup module-toggle API is missing." >&2
  exit 1
fi
if grep -qE 'findCheckBox|findMarkedCheckBox|commonParent' src/com/wtm/modular/ui/WorkspaceLifecycleV3.java; then
  echo "ERROR: WorkspaceLifecycleV3 contains stale Settings component discovery." >&2
  exit 1
fi

# Settings pages own their scroll layout through scrollableSettingsPage().
if ! grep -q 'scrollableSettingsPage' src/com/wtm/ui/SettingsDialog.java || \
   ! grep -q 'VERTICAL_SCROLLBAR_AS_NEEDED' src/com/wtm/ui/SettingsDialog.java || \
   ! grep -q 'HORIZONTAL_SCROLLBAR_NEVER' src/com/wtm/ui/SettingsDialog.java; then
  echo "ERROR: canonical Settings scroll ownership is missing." >&2
  exit 1
fi

# Employee layout and compact directory presentation belong to the canonical
# EmployeeOperationsPanel.
if ! grep -q 'hideDirectoryColumn("Employee #")' src/com/wtm/ui/EmployeeOperationsPanel.java || \
   ! grep -q 'split.setDividerSize(0)' src/com/wtm/ui/EmployeeOperationsPanel.java; then
  echo "ERROR: canonical Employee presentation ownership is missing from EmployeeOperationsPanel." >&2
  exit 1
fi

# Dynamic workspace integrations must use the canonical source-backed extension
# API instead of reflecting into OperationsWorkspaceFrame private implementation
# details. Keep these checks in build.sh so local release builds and CI enforce
# the same architecture.
for file in \
  src/com/wtm/app/AiEnabledMain.java \
  src/com/wtm/modular/ui/MusicWorkspaceModule.java \
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

# Dashboard extension identity belongs to OperationsWorkspaceFrame. Optional
# modules must not recursively scan the Swing tree to rediscover or remove
# surfaces that the workspace itself mounted.
for api in 'mountDashboardExtension(String id' 'removeDashboardExtension(String id)'; do
  if ! grep -Fq "$api" src/com/wtm/ui/OperationsWorkspaceFrame.java; then
    echo "ERROR: source-owned dashboard extension API '$api' is missing." >&2
    exit 1
  fi
done
if grep -qE 'findByName|findMarked' src/com/wtm/app/AiEnabledMain.java src/com/wtm/modular/ui/MusicWorkspaceModule.java || \
   grep -qE 'findMarked|removeMarked|removeNamed' src/com/wtm/modular/ui/WorkspaceLifecycleV3.java; then
  echo "ERROR: recursive dashboard extension component discovery returned." >&2
  exit 1
fi

# Music settings belong to the canonical application data directory and own
# their own file hardening. Workspace lifecycle code must not reach through
# into Music persistence details.
if ! grep -Fq 'ConfigService.appDataDir().resolve("music.properties")' src/com/wtm/modular/ui/MusicWorkspaceModule.java || \
   ! grep -Fq 'SecureFiles.storePropertiesAtomic(config,p,"NorthStar Music & Audio")' src/com/wtm/modular/ui/MusicWorkspaceModule.java; then
  echo "ERROR: Music settings are not using canonical hardened app storage." >&2
  exit 1
fi
if grep -q 'restrictMusicStorage' src/com/wtm/modular/ui/WorkspaceLifecycleV3.java; then
  echo "ERROR: workspace lifecycle reached into Music storage ownership." >&2
  exit 1
fi

# Canonical source must not rediscover UI/runtime ownership through reflection,
# global AWT listeners, open-window scans, or class-name matching. getSimpleName
# is intentionally excluded because it is used only for harmless error labels.
if grep -R -n --include='*.java' -E 'Class\.forName|getClass\(\)\.getName|\.getName\(\)\.contains|addAWTEventListener|Window\.getWindows' src; then
  echo "ERROR: dynamic/global runtime discovery returned to canonical source." >&2
  exit 1
fi

# The retired standalone .northstar directory may appear only in the explicit
# one-time Music migration path; active hardening/storage must use ConfigService.
if grep -Fq 'Paths.get(System.getProperty("user.home"), ".northstar")' src/com/wtm/security/LocalSecurityHardening.java; then
  echo "ERROR: LocalSecurityHardening still targets retired .northstar storage." >&2
  exit 1
fi

# ConfigService is the sole owner of the canonical application-data root.
# Features derive their storage paths from appDataDir() rather than rebuilding
# the product directory literal independently.
if grep -R -n --include='*.java' --exclude='ConfigService.java' -F '.northstar-operations-intelligence' src; then
  echo "ERROR: canonical app-data directory literal escaped ConfigService ownership." >&2
  exit 1
fi

# Dead compatibility-only UI/sports shims must not return.
if grep -R -n --include='*.java' -E 'slotWidthLegacy|Retained for source compatibility with older integrations' src; then
  echo "ERROR: dead compatibility-only code returned." >&2
  exit 1
fi

# Redundant workspace movement compatibility state and stale preview markers
# must not return to canonical source.
if grep -R -n --include='*.java' -E 'workspaceInfoAutoScroll|northStarPreviewV501' src; then
  echo "ERROR: retired compatibility state returned." >&2
  exit 1
fi
# Old product identity may remain only in ConfigService for one-time migration.
if grep -R -n --include='*.java' --exclude='ConfigService.java' -F 'WeatherTrafficMonitor/' src; then
  echo "ERROR: stale WeatherTrafficMonitor identity returned to active source." >&2
  exit 1
fi

# Intelligence enablement is canonical application configuration. The old
# java.util.prefs value is read only by ConfigService as a compatibility fallback;
# WorkspaceLifecycleV3 must not maintain a second source of truth.
if grep -qE 'java\.util\.prefs|Preferences|WORKSPACE_PREFS|INTELLIGENCE_PREF|setModuleToken' src/com/wtm/modular/ui/WorkspaceLifecycleV3.java; then
  echo "ERROR: Intelligence workspace enablement has duplicate Preferences ownership." >&2
  exit 1
fi
if ! grep -Fq 'public boolean workspaceIntelligenceEnabled = true;' src/com/wtm/config/AppConfig.java || \
   ! grep -Fq 'workspace.intelligence.enabled' src/com/wtm/config/ConfigService.java; then
  echo "ERROR: source-owned Intelligence configuration is missing." >&2
  exit 1
fi

# Real FlatLaf owns ordinary Swing controls. North Star may layer branded semantics,
# but custom ComboBox/TabbedPane look-and-feel delegates must not return.
if [ ! -f src/com/wtm/ui/ThemeManager.java ] || \
   ! grep -Fq 'FlatDarkLaf.setup()' src/com/wtm/ui/ThemeManager.java || \
   grep -qE 'ThemedComboBoxUI|BasicTabbedPaneUI' src/com/wtm/ui/ThemeStyler.java || \
   [ -f src/com/wtm/ui/ThemedComboBoxUI.java ]; then
  echo "ERROR: canonical theme ownership is not delegated to real FlatLaf." >&2
  exit 1
fi

if [ ! -f lib/flatlaf-3.7.2.jar ] || [ ! -f lib/flatlaf-intellij-themes-3.7.2.jar ]; then
  echo "ERROR: required FlatLaf 3.7.2 runtime libraries are missing from lib/." >&2
  exit 1
fi

rm -rf out
mkdir -p out
javac --release 21 -Xlint:unchecked -Werror -encoding UTF-8 -cp 'lib/*' -d out $(find src -name '*.java')
for dep in lib/flatlaf-3.7.2.jar lib/flatlaf-intellij-themes-3.7.2.jar; do
  (cd out && jar --extract --file "../$dep")
done
rm -f out/META-INF/MANIFEST.MF
if [ -d resources ]; then cp -R resources/. out/; fi
jar --create --file NorthStarOperations.jar --manifest MANIFEST.MF -C out .

DUPLICATES=$(zipinfo -1 NorthStarOperations.jar | sort | uniq -d | wc -l | tr -d ' ')
if [ "$DUPLICATES" != "0" ]; then
  echo "ERROR: built JAR contains duplicate archive entries." >&2
  exit 1
fi

unzip -tq NorthStarOperations.jar >/dev/null

echo "Built NorthStarOperations.jar from canonical master src tree"
