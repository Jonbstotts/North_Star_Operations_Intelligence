package com.wtm.ingest;

public record IngestionRecord(String id,String receivedAt,String source,String originalName,String sha256,String detectedType,String status,int records,String message,String archivedPath) {}
