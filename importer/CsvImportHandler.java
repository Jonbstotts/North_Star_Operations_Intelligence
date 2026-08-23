package com.wtm.importer; import java.nio.file.Path;
/** Shared domain adapter: Employee imports use this now; daily KPI imports can implement it later. */
public interface CsvImportHandler<T>{ImportResult importFile(Path file,T target)throws Exception;}