package com.wtm.importer;

import java.nio.file.Path;
import java.util.*;

public final class KpiCsvImporter implements CsvImportHandler<Object>{
    private static final List<KpiCsvImportProfile>PROFILES=List.of(
            new DailyLhyKpiProfile(),new FloorDenialKpiProfile(),new DamageKpiProfile());

    @Override public ImportResult importFile(Path file,Object ignored)throws Exception{
        KpiImportResult r=importKpis(file);
        return new ImportResult(r.rowsRead(),0,r.metrics().size(),0,0,r.warnings());
    }

    public KpiImportResult previewKpis(Path file)throws Exception{
        CsvTable t=CsvTable.read(file);
        for(KpiCsvImportProfile p:PROFILES)
            if(p.supports(t))return p.parse(t);
        throw new IllegalArgumentException(
                "Unrecognized KPI CSV format. Supported reports currently include "
                +"Daily LHY / LPH, Floor Denials, and Damages.");
    }

    public void commit(KpiImportResult result)throws Exception{
        KpiHistoryStore.merge(result);
    }

    public KpiImportResult importKpis(Path file)throws Exception{
        KpiImportResult r=previewKpis(file);
        commit(r);
        return r;
    }
}
