package com.limoz.fleet.reporting.export;

public record ExportedReport(String fileName, String contentType, byte[] content) {}
