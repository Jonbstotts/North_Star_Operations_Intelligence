package com.wtm.importer;
public interface KpiCsvImportProfile{boolean supports(CsvTable table);KpiImportResult parse(CsvTable table)throws Exception;}
