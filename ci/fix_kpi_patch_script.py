from pathlib import Path

p=Path('ci/one_shot_restore_kpi_ticker_grid_settings.py')
text=p.read_text()
text=text.replace('if count != 3:\n    raise SystemExit(f"expected 3 workspaceMetricColumnCount references, found {count}")',
                  'if count != 4:\n    raise SystemExit(f"expected 4 workspaceMetricColumnCount references, found {count}")',1)
text=text.replace('    private int informationMetricColumnCount(){\n        long enabled=config.operationsKpis.stream()',
                  '    private int workspaceMetricColumnCount(){\n        long enabled=config.operationsKpis.stream()',1)
text=text.replace('        int columns=informationMetricColumnCount();\n        JPanel metrics=new JPanel(new GridLayout(1,columns,10,0));',
                  '        int columns=workspaceMetricColumnCount();\n        JPanel metrics=new JPanel(new GridLayout(1,columns,10,0));',1)
p.write_text(text)
print('KPI_PATCH_SCRIPT_CORRECTED')
