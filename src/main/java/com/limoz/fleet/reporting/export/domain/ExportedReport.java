package com.limoz.fleet.reporting.export.domain;

public record ExportedReport(String fileName, String contentType, byte[] content) {}
