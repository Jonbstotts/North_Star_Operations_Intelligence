from pathlib import Path
import runpy

path=Path('ci/ConfigRoundTripSmokeTest.java')
text=path.read_text()
old='''            AppConfig source = new AppConfig();\n            source.basemapProvider = "OPENSTREETMAP";\n            source.workspaceInfoBlockCount = 8;'''
new='''            AppConfig source = new AppConfig();\n            source.workspaceInfoBlockCount = 8;\n            source.basemapProvider = "OPENSTREETMAP";'''
if old not in text:
    raise SystemExit('basemap source normalization anchor not found')
text=text.replace(old,new,1)
old='''            AppConfig loaded = ConfigService.load();\n\n            require("OPENSTREETMAP".equals(loaded.basemapProvider),\n                    "basemap provider did not round-trip");\n            require(loaded.workspaceInfoBlockCount == 8,'''
new='''            AppConfig loaded = ConfigService.load();\n\n            require(loaded.workspaceInfoBlockCount == 8,'''
if old not in text:
    raise SystemExit('basemap verification normalization anchor not found')
text=text.replace(old,new,1)
old='''            require(loaded.workspaceInfoBlockCount == 8,\n                    "information visible-count did not round-trip");'''
new='''            require(loaded.workspaceInfoBlockCount == 8,\n                    "information visible-count did not round-trip");\n            require("OPENSTREETMAP".equals(loaded.basemapProvider),\n                    "basemap provider did not round-trip");'''
if old not in text:
    raise SystemExit('basemap verification reinsertion anchor not found')
text=text.replace(old,new,1)
path.write_text(text)

runpy.run_path('tmp/repair_ticker_theme.py',run_name='__main__')
