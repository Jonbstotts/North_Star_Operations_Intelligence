from pathlib import Path
import runpy

build=Path('build.sh')
text=build.read_text()
old='''  ci/WeatherAlertPolicySmokeTest.java \\\n  ci/DashboardGridMigrationSmokeTest.java \\\n  ci/ConfigRoundTripSmokeTest.java \\\n  ci/BasemapProviderSmokeTest.java\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' WeatherAlertPolicySmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' DashboardGridMigrationSmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' ConfigRoundTripSmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' BasemapProviderSmokeTest'''
new='''  ci/WeatherAlertPolicySmokeTest.java \\\n  ci/DashboardGridMigrationSmokeTest.java \\\n  ci/ConfigRoundTripSmokeTest.java\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' WeatherAlertPolicySmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' DashboardGridMigrationSmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' ConfigRoundTripSmokeTest'''
if old not in text:
    raise SystemExit('basemap build normalization anchor not found')
build.write_text(text.replace(old,new,1))

runpy.run_path('tmp/repair_ticker_theme_v2.py',run_name='__main__')

text=build.read_text()
old='''  ci/ConfigRoundTripSmokeTest.java \\\n  ci/TickerGeometrySmokeTest.java\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' WeatherAlertPolicySmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' DashboardGridMigrationSmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' ConfigRoundTripSmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' TickerGeometrySmokeTest'''
new='''  ci/ConfigRoundTripSmokeTest.java \\\n  ci/TickerGeometrySmokeTest.java \\\n  ci/BasemapProviderSmokeTest.java\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' WeatherAlertPolicySmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' DashboardGridMigrationSmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' ConfigRoundTripSmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' TickerGeometrySmokeTest\njava -Djava.awt.headless=true -cp '/tmp/ns-foundation-smoke:out:lib/*' BasemapProviderSmokeTest'''
if old not in text:
    raise SystemExit('basemap build reinsertion anchor not found')
build.write_text(text.replace(old,new,1))
