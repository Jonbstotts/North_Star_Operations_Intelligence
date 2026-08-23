package com.wtm.importer;
import java.time.LocalDate;
public record KpiImportedMetric(String metricId,String label,double value,String unit,LocalDate effectiveDate,String sourceId){}
