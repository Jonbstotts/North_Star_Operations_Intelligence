from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str) -> None:
    file = Path(path)
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"{label} anchor not found in {path}")
    file.write_text(text.replace(old, new, 1))


# ---------------------------------------------------------------------------
# Theme ownership: branded UI defaults must be scoped to the installed LAF.
# ---------------------------------------------------------------------------
Path("src/com/wtm/ui/ThemeManager.java").write_text(r'''package com.wtm.ui;

import com.formdev.flatlaf.*;
import com.formdev.flatlaf.intellijthemes.*;

import javax.swing.*;
import java.awt.*;

/** Single owner for installing the Swing look-and-feel. */
public final class ThemeManager {
    private ThemeManager(){}

    /*
     * Older builds wrote the North Star overlay through UIManager.put(). Those
     * values live in Swing's developer-default table and survive subsequent
     * look-and-feel installs, which caused North Star colors to leak into every
     * FlatLaf theme. Clear only the keys North Star historically owned before
     * each install, then apply branded values to the current LAF defaults.
     */
    private static final String[] BRAND_KEYS={
            "Component.accentColor",
            "Component.focusColor",
            "TabbedPane.underlineColor",
            "ProgressBar.foreground",
            "TextComponent.selectionBackground",
            "Table.selectionBackground",
            "List.selectionBackground",
            "Panel.background",
            "Label.foreground"
    };

    public static void install(AppTheme theme){
        AppTheme resolved=theme==null?AppTheme.NORTH_STAR:theme;
        clearLegacyBrandOverrides();

        boolean installed=switch(resolved){
            case FLATLAF_LIGHT->FlatLightLaf.setup();
            case FLATLAF_DARK->FlatDarkLaf.setup();
            case FLATLAF_INTELLIJ->FlatIntelliJLaf.setup();
            case FLATLAF_DARCULA->FlatDarculaLaf.setup();
            case ARC->FlatArcIJTheme.setup();
            case ARC_DARK->FlatArcDarkIJTheme.setup();
            case CARBON->FlatCarbonIJTheme.setup();
            case COBALT_2->FlatCobalt2IJTheme.setup();
            case DRACULA->FlatDraculaIJTheme.setup();
            case NORD->FlatNordIJTheme.setup();
            case ONE_DARK->FlatOneDarkIJTheme.setup();
            case SOLARIZED_LIGHT->FlatSolarizedLightIJTheme.setup();
            case SOLARIZED_DARK->FlatSolarizedDarkIJTheme.setup();
            case GRADIANTO_MIDNIGHT->FlatGradiantoMidnightBlueIJTheme.setup();
            case GRADIANTO_NATURE->FlatGradiantoNatureGreenIJTheme.setup();
            case ARC_DARK_ORANGE->FlatArcDarkOrangeIJTheme.setup();
            case HIGH_CONTRAST->FlatHighContrastIJTheme.setup();
            case CHRISTMAS->FlatArcDarkIJTheme.setup();
            case HALLOWEEN->FlatDarkPurpleIJTheme.setup();
            case THANKSGIVING->FlatArcDarkOrangeIJTheme.setup();
            case INDEPENDENCE->FlatCobalt2IJTheme.setup();
            case VALENTINE->FlatLightFlatIJTheme.setup();
            case ST_PATRICKS->FlatGradiantoNatureGreenIJTheme.setup();
            case WINTER_FROST->FlatCyanLightIJTheme.setup();
            case NORTH_STAR->FlatDarculaLaf.setup();
        };

        if(!installed){
            clearLegacyBrandOverrides();
            FlatDarkLaf.setup();
        }
        if(resolved.branded())applyBrandOverlay(resolved);
    }

    public static void refresh(Component root){
        if(root==null)return;
        if(root instanceof JComponent component)SwingUtilities.updateComponentTreeUI(component);
        else if(root instanceof Window window)SwingUtilities.updateComponentTreeUI(window);
    }

    public static Color uiColor(String key,Color fallback){
        Color value=UIManager.getColor(key);
        return value==null?fallback:value;
    }

    private static void clearLegacyBrandOverrides(){
        for(String key:BRAND_KEYS)
            UIManager.put(key,null);
    }

    /**
     * Brand/holiday overlays belong to the currently installed look-and-feel.
     * Writing directly to its defaults makes the override disappear naturally
     * when another FlatLaf is installed instead of contaminating future themes.
     */
    private static void applyBrandOverlay(AppTheme theme){
        UIDefaults defaults=UIManager.getLookAndFeelDefaults();
        defaults.put("Component.accentColor",theme.accent());
        defaults.put("Component.focusColor",theme.accent());
        defaults.put("TabbedPane.underlineColor",theme.accent());
        defaults.put("ProgressBar.foreground",theme.accent());
        defaults.put("TextComponent.selectionBackground",theme.accent());
        defaults.put("Table.selectionBackground",theme.accent());
        defaults.put("List.selectionBackground",theme.accent());
        defaults.put("Panel.background",theme.bg());
        defaults.put("Label.foreground",theme.text());
    }
}
''')


# Theme selection is previewed by swatches until Save & Apply. Swing LAF is a
# process-wide singleton, so changing it for a detached Settings page while the
# workspace remains mounted creates a mixed-theme application.
replace_once(
    "src/com/wtm/ui/SettingsDialog.java",
    '''        themeSelector.addActionListener(e->{\n            updateThemePreview();\n            AppTheme selected=(AppTheme)themeSelector.getSelectedItem();\n            if(selected!=null)applySettingsTheme(selected);\n        });''',
    '''        themeSelector.addActionListener(e->updateThemePreview());''',
    "settings theme selector"
)

replace_once(
    "src/com/wtm/ui/SettingsDialog.java",
    '''    /**\n     * Live-preview the chosen theme across the entire Settings window.\n     * Nothing is persisted until Save & Apply.\n     */\n    private void applySettingsTheme(AppTheme theme){''',
    '''    /**\n     * Applies the already-selected application theme to this Settings session.\n     * Alternative choices are previewed by swatches until Save & Apply because\n     * Swing look-and-feel is process-wide and cannot safely preview only one\n     * detached workspace page.\n     */\n    private void applySettingsTheme(AppTheme theme){''',
    "settings theme ownership comment"
)

replace_once(
    "src/com/wtm/ui/SettingsDialog.java",
    '''        themePreview.setBorder(BorderFactory.createTitledBorder("Theme preview"));\n        addFull(p,y++,themePreview);\n\n        addFull(p,y++,automaticHolidayThemes);''',
    '''        themePreview.setBorder(BorderFactory.createTitledBorder("Theme preview"));\n        addFull(p,y++,themePreview);\n        addFull(p,y++,new JLabel(\n                "<html>Theme choices are previewed here without partially recoloring the live workspace. "\n              + "Choose <b>Save & Apply</b> to install the selected FlatLaf across North Star.</html>"));\n\n        addFull(p,y++,automaticHolidayThemes);''',
    "theme preview help"
)

# Rebuilding after Save installs the selected LAF before construction; refresh
# the completed source-owned workspace once so every standard delegate and
# branded semantic component shares the same final theme boundary.
replace_once(
    "src/com/wtm/ui/OperationsWorkspaceFrame.java",
    '''    private void applyConfig(AppConfig updated){\n        embeddedSettingsSession=null;\n        config=updated;\n        buildUi();\n        startRefreshers();\n    }''',
    '''    private void applyConfig(AppConfig updated){\n        embeddedSettingsSession=null;\n        config=updated;\n        buildUi();\n        ThemeStyler.apply(this,Theme.active());\n        startRefreshers();\n    }''',
    "workspace settings apply"
)


# ---------------------------------------------------------------------------
# Shared continuous-ticker geometry. One cycle contains only equal-width item
# slots; enough identical cycles are rendered to cover the viewport at every
# possible reset position. There is no special end spacer/seam.
# ---------------------------------------------------------------------------
Path("src/com/wtm/ui/ContinuousTickerGeometry.java").write_text(r'''package com.wtm.ui;

/**
 * Calculates seamless geometry for dashboard continuous tickers.
 *
 * A cycle is exactly {@code itemCount * slotWidth}. Repeating that identical
 * cycle enough times to cover {@code cycleWidth + viewportWidth} guarantees
 * that wrapping the viewport by one cycle is visually indistinguishable from
 * ordinary movement, even when fewer items exist than visible slots.
 */
public final class ContinuousTickerGeometry {
    private ContinuousTickerGeometry(){}

    public static Layout calculate(
            int viewportWidth,
            int visibleSlots,
            int itemCount,
            int minimumSlotWidth
    ){
        int width=Math.max(1,viewportWidth);
        int slots=Math.max(1,visibleSlots);
        int items=Math.max(1,itemCount);
        int minimum=Math.max(1,minimumSlotWidth);

        int slotWidth=Math.max(
                minimum,
                (int)Math.ceil(width/(double)slots)
        );
        int cycleWidth=Math.multiplyExact(items,slotWidth);
        int copies=Math.max(
                2,
                1+(int)Math.ceil(width/(double)cycleWidth)
        );
        int trackWidth=Math.multiplyExact(cycleWidth,copies);
        return new Layout(slotWidth,cycleWidth,copies,trackWidth);
    }

    public record Layout(
            int slotWidth,
            int cycleWidth,
            int copies,
            int trackWidth
    ){}
}
''')

# Information ticker: remove the seam spacer and render geometry-owned copies.
old_info='''        int slotWidth=Math.max(\n                150,\n                availableWidth/visibleSlots\n        );\n\n        informationTickerTrack=new JPanel();\n        informationTickerTrack.setOpaque(false);\n        informationTickerTrack.setLayout(\n                new BoxLayout(informationTickerTrack,BoxLayout.X_AXIS));\n\n        for(String type:configured){\n            JComponent metric=workspaceInfoMetric(type);\n            metric.setPreferredSize(new Dimension(slotWidth,76));\n            metric.setMinimumSize(new Dimension(slotWidth,76));\n            metric.setMaximumSize(new Dimension(slotWidth,76));\n            informationTickerTrack.add(metric);\n        }\n\n        informationTickerTrack.add(Box.createHorizontalStrut(slotWidth/2));\n\n        informationTickerCycleWidth=\n                configured.size()*slotWidth+(slotWidth/2);\n\n        /*\n         * Duplicate one complete cycle so resetting the viewport position is\n         * visually seamless rather than snapping the Information row.\n         */\n        for(String type:configured){\n            JComponent metric=workspaceInfoMetric(type);\n            metric.setPreferredSize(new Dimension(slotWidth,76));\n            metric.setMinimumSize(new Dimension(slotWidth,76));\n            metric.setMaximumSize(new Dimension(slotWidth,76));\n            informationTickerTrack.add(metric);\n        }\n\n        informationTickerTrack.setPreferredSize(\n                new Dimension(\n                        informationTickerCycleWidth*2,\n                        76\n                )\n        );'''
new_info='''        ContinuousTickerGeometry.Layout geometry=\n                ContinuousTickerGeometry.calculate(\n                        availableWidth,\n                        visibleSlots,\n                        configured.size(),\n                        150\n                );\n        int slotWidth=geometry.slotWidth();\n\n        informationTickerTrack=new JPanel();\n        informationTickerTrack.setOpaque(false);\n        informationTickerTrack.setLayout(\n                new BoxLayout(informationTickerTrack,BoxLayout.X_AXIS));\n\n        for(int copy=0;copy<geometry.copies();copy++){\n            for(String type:configured){\n                JComponent metric=workspaceInfoMetric(type);\n                sizeTickerMetric(metric,slotWidth);\n                informationTickerTrack.add(metric);\n            }\n        }\n\n        informationTickerCycleWidth=geometry.cycleWidth();\n        informationTickerTrack.setPreferredSize(\n                new Dimension(geometry.trackWidth(),76));'''
replace_once(
    "src/com/wtm/ui/OperationsWorkspaceFrame.java",
    old_info,
    new_info,
    "information ticker geometry"
)

old_ops='''        int slotWidth=Math.max(150,availableWidth/visibleSlots);\n\n        operationsTickerTrack=new JPanel();\n        operationsTickerTrack.setOpaque(false);\n        operationsTickerTrack.setLayout(\n                new BoxLayout(operationsTickerTrack,BoxLayout.X_AXIS));\n\n        for(OperationsKpiConfig kpi:enabled){\n            JComponent metric=kpiCard(kpi);\n            metric.setPreferredSize(new Dimension(slotWidth,76));\n            metric.setMinimumSize(new Dimension(slotWidth,76));\n            metric.setMaximumSize(new Dimension(slotWidth,76));\n            operationsTickerTrack.add(metric);\n        }\n        operationsTickerTrack.add(Box.createHorizontalStrut(slotWidth/2));\n        operationsTickerCycleWidth=enabled.size()*slotWidth+(slotWidth/2);\n\n        for(OperationsKpiConfig kpi:enabled){\n            JComponent metric=kpiCard(kpi);\n            metric.setPreferredSize(new Dimension(slotWidth,76));\n            metric.setMinimumSize(new Dimension(slotWidth,76));\n            metric.setMaximumSize(new Dimension(slotWidth,76));\n            operationsTickerTrack.add(metric);\n        }\n        operationsTickerTrack.setPreferredSize(\n                new Dimension(operationsTickerCycleWidth*2,76));'''
new_ops='''        ContinuousTickerGeometry.Layout geometry=\n                ContinuousTickerGeometry.calculate(\n                        availableWidth,\n                        visibleSlots,\n                        enabled.size(),\n                        150\n                );\n        int slotWidth=geometry.slotWidth();\n\n        operationsTickerTrack=new JPanel();\n        operationsTickerTrack.setOpaque(false);\n        operationsTickerTrack.setLayout(\n                new BoxLayout(operationsTickerTrack,BoxLayout.X_AXIS));\n\n        for(int copy=0;copy<geometry.copies();copy++){\n            for(OperationsKpiConfig kpi:enabled){\n                JComponent metric=kpiCard(kpi);\n                sizeTickerMetric(metric,slotWidth);\n                operationsTickerTrack.add(metric);\n            }\n        }\n        operationsTickerCycleWidth=geometry.cycleWidth();\n        operationsTickerTrack.setPreferredSize(\n                new Dimension(geometry.trackWidth(),76));'''
replace_once(
    "src/com/wtm/ui/OperationsWorkspaceFrame.java",
    old_ops,
    new_ops,
    "operations ticker geometry"
)

# Shared fixed-slot sizing keeps the two ticker implementations structurally
# identical and prevents future spacing fixes from diverging.
replace_once(
    "src/com/wtm/ui/OperationsWorkspaceFrame.java",
    '''    private void startInformationTicker(){\n        if(informationTickerViewport==null''',
    '''    private static void sizeTickerMetric(JComponent metric,int slotWidth){\n        Dimension size=new Dimension(slotWidth,76);\n        metric.setPreferredSize(size);\n        metric.setMinimumSize(size);\n        metric.setMaximumSize(size);\n    }\n\n    private void startInformationTicker(){\n        if(informationTickerViewport==null''',
    "shared ticker metric sizing"
)


# ---------------------------------------------------------------------------
# Regression tests.
# ---------------------------------------------------------------------------
Path("ci/TickerGeometrySmokeTest.java").write_text(r'''import com.wtm.ui.ContinuousTickerGeometry;

public final class TickerGeometrySmokeTest {
    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    public static void main(String[] args){
        for(int width:new int[]{640,900,1200,1600,1920}){
            for(int visible=2;visible<=8;visible++){
                for(int items=1;items<=20;items++){
                    ContinuousTickerGeometry.Layout layout=
                            ContinuousTickerGeometry.calculate(
                                    width,visible,items,150);

                    require(layout.slotWidth()>=150,
                            "slot width fell below minimum");
                    require(layout.cycleWidth()==items*layout.slotWidth(),
                            "cycle contains an artificial seam gap");
                    require(layout.copies()>=2,
                            "ticker must contain at least two cycles");
                    require(layout.trackWidth()==
                                    layout.cycleWidth()*layout.copies(),
                            "track width does not match complete cycles");
                    require(layout.trackWidth()>=
                                    layout.cycleWidth()+width,
                            "track cannot cover viewport at wrap boundary");
                }
            }
        }
        System.out.println("TICKER_GEOMETRY_SMOKE_OK");
    }
}
''')

# Strengthen the existing theme smoke test so switching away from North Star
# proves that branded developer defaults do not survive the LAF change.
replace_once(
    "ci/ThemeSmokeTest.java",
    '''import java.awt.image.BufferedImage;\n''',
    '''import java.awt.image.BufferedImage;\nimport java.util.Objects;\n''',
    "theme smoke imports"
)
replace_once(
    "ci/ThemeSmokeTest.java",
    '''    private static void verifyFlatLaf(AppTheme theme) {\n        LookAndFeel laf = UIManager.getLookAndFeel();\n        if (laf == null || !laf.getClass().getName().startsWith("com.formdev.flatlaf"))\n            throw new IllegalStateException(theme.id() + ": non-FlatLaf look and feel " + laf);\n        if (Theme.bg() == null || Theme.panel() == null || Theme.panel2() == null\n                || Theme.border() == null || Theme.text() == null || Theme.muted() == null\n                || Theme.accent() == null)\n            throw new IllegalStateException(theme.id() + ": unresolved semantic color");\n    }''',
    '''    private static void verifyFlatLaf(AppTheme theme) {\n        LookAndFeel laf = UIManager.getLookAndFeel();\n        if (laf == null || !laf.getClass().getName().startsWith("com.formdev.flatlaf"))\n            throw new IllegalStateException(theme.id() + ": non-FlatLaf look and feel " + laf);\n        if (Theme.bg() == null || Theme.panel() == null || Theme.panel2() == null\n                || Theme.border() == null || Theme.text() == null || Theme.muted() == null\n                || Theme.accent() == null)\n            throw new IllegalStateException(theme.id() + ": unresolved semantic color");\n\n        for (String key : new String[]{\n                "Component.accentColor",\n                "Panel.background",\n                "Label.foreground",\n                "Table.selectionBackground",\n                "List.selectionBackground"\n        }) {\n            Object effective = UIManager.get(key);\n            Object lafDefault = UIManager.getLookAndFeelDefaults().get(key);\n            if (!Objects.equals(effective, lafDefault))\n                throw new IllegalStateException(\n                        theme.id() + ": stale developer override for " + key);\n        }\n    }''',
    "theme smoke override verification"
)

# Theme choice itself is part of the settings persistence contract.
replace_once(
    "ci/ConfigRoundTripSmokeTest.java",
    '''            AppConfig source = new AppConfig();\n            source.workspaceInfoBlockCount = 8;''',
    '''            AppConfig source = new AppConfig();\n            source.themeId = "FLATLAF_LIGHT";\n            source.automaticHolidayThemes = false;\n            source.workspaceInfoBlockCount = 8;''',
    "config theme source"
)
replace_once(
    "ci/ConfigRoundTripSmokeTest.java",
    '''            AppConfig loaded = ConfigService.load();\n\n            require(loaded.workspaceInfoBlockCount == 8,''',
    '''            AppConfig loaded = ConfigService.load();\n\n            require("FLATLAF_LIGHT".equals(loaded.themeId),\n                    "selected FlatLaf theme did not round-trip");\n            require(!loaded.automaticHolidayThemes,\n                    "automatic holiday-theme choice did not round-trip");\n            require(loaded.workspaceInfoBlockCount == 8,''',
    "config theme verification"
)

# Compile/run the ticker regression with the existing foundation suite.
replace_once(
    "build.sh",
    '''  ci/WeatherAlertPolicySmokeTest.java \\\n  ci/DashboardGridMigrationSmokeTest.java \\\n  ci/ConfigRoundTripSmokeTest.java\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' WeatherAlertPolicySmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' DashboardGridMigrationSmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' ConfigRoundTripSmokeTest''',
    '''  ci/WeatherAlertPolicySmokeTest.java \\\n  ci/DashboardGridMigrationSmokeTest.java \\\n  ci/ConfigRoundTripSmokeTest.java \\\n  ci/TickerGeometrySmokeTest.java\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' WeatherAlertPolicySmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' DashboardGridMigrationSmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' ConfigRoundTripSmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' TickerGeometrySmokeTest''',
    "ticker smoke build gate"
)

# Permanent source guards: no artificial seam spacer and branded overlays must
# remain scoped to the current LAF defaults.
replace_once(
    "build.sh",
    '''# Dashboard layout editing is owned by the top-right dashboard gear; the old\n# in-content Customize/Reset button strip must not return. The configured\n# scrolling ticker is rendered by the source-owned HeaderTicker.''',
    '''# Continuous dashboard tickers share one geometry owner. A cycle contains\n# equal item slots only; artificial end spacers recreate the visible reset gap.\nif [ ! -f src/com/wtm/ui/ContinuousTickerGeometry.java ] || \\\n   grep -Fq 'informationTickerTrack.add(Box.createHorizontalStrut' src/com/wtm/ui/OperationsWorkspaceFrame.java || \\\n   grep -Fq 'operationsTickerTrack.add(Box.createHorizontalStrut' src/com/wtm/ui/OperationsWorkspaceFrame.java; then\n  echo "ERROR: seamless continuous-ticker geometry regression detected." >&2\n  exit 1\nfi\n\n# Dashboard layout editing is owned by the top-right dashboard gear; the old\n# in-content Customize/Reset button strip must not return. The configured\n# scrolling ticker is rendered by the source-owned HeaderTicker.''',
    "ticker source guard"
)
replace_once(
    "build.sh",
    '''if [ ! -f src/com/wtm/ui/ThemeManager.java ] || \\\n   ! grep -Fq 'FlatDarkLaf.setup()' src/com/wtm/ui/ThemeManager.java || \\\n   grep -qE 'ThemedComboBoxUI|BasicTabbedPaneUI' src/com/wtm/ui/ThemeStyler.java || \\\n   [ -f src/com/wtm/ui/ThemedComboBoxUI.java ]; then''',
    '''if [ ! -f src/com/wtm/ui/ThemeManager.java ] || \\\n   ! grep -Fq 'FlatDarkLaf.setup()' src/com/wtm/ui/ThemeManager.java || \\\n   ! grep -Fq 'UIManager.getLookAndFeelDefaults()' src/com/wtm/ui/ThemeManager.java || \\\n   grep -qE 'ThemedComboBoxUI|BasicTabbedPaneUI' src/com/wtm/ui/ThemeStyler.java || \\\n   [ -f src/com/wtm/ui/ThemedComboBoxUI.java ]; then''',
    "theme source guard"
)

print("ticker/theme repair staged")
