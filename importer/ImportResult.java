package com.wtm.importer; import java.util.List;
public record ImportResult(int rowsRead,int added,int updated,int unchanged,int skipped,List<String>warnings){
 public ImportResult{warnings=warnings==null?List.of():List.copyOf(warnings);}
 public String summary(){String b="Rows: "+rowsRead+"   Added: "+added+"   Updated: "+updated+"   Unchanged: "+unchanged+"   Skipped: "+skipped;return warnings.isEmpty()?b:b+"\n\nWarnings:\n• "+String.join("\n• ",warnings);}
}