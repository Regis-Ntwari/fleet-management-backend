package com.limoz.fleet.reporting.export.domain;

import org.springframework.http.MediaType;

public enum ReportFormat {
    JSON("json", MediaType.APPLICATION_JSON_VALUE),
    CSV("csv", "text/csv"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PDF("pdf", MediaType.APPLICATION_PDF_VALUE);

    private final String extension;
    private final String contentType;

    ReportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }
}
