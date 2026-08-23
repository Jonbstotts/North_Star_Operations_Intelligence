# North Star KPI CSV Import

North Star v1.1.9 uses a profile-based KPI CSV importer. The entry point is `KpiCsvImporter`; individual report formats implement `KpiCsvImportProfile`. Imported daily values are merged into a common long-form history file at:

`~/.northstar-operations-intelligence/kpi-history.csv`

This makes later report adapters (picks, damages, outbound volume, attendance, etc.) additive rather than requiring a new import framework.

## Daily LHY / LPH report

Recognized by the presence of `LOGDATE` and `LHY`. When `Order Lines` is present it is also imported.

- `LHY` -> **LHY Performance**
- `Order Lines` -> **Lines Shipped**
- Every completed daily value is written to KPI history.
- The dashboard is updated from the latest completed value for each metric. This means an in-progress current-day row with a blank LHY does not erase the previous completed day's KPI.

## Floor Denials report

Recognized by `Denial Type`, `Date`, `Order`, and `Line`.

A single denial may appear on multiple RF task rows. North Star therefore counts distinct daily business denials using:

`Date(day) + Order + Line + Item + Denial Type`

rather than simply counting CSV rows. Every daily denial count is stored in KPI history and the latest dated count is applied to the Floor Denials KPI.

## Import workflow

Open **Workspace Setup** and use **Import KPI CSV** under the Operations Snapshot KPI table. North Star previews the detected report, date range, number of daily records stored, and values that will be applied. After accepting the preview, choose **Save & Apply** to persist the dashboard values.
