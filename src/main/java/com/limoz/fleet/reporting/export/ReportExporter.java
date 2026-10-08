package com.limoz.fleet.reporting.export;

public interface ReportExporter {

    ReportFormat format();

    byte[] export(ReportTable table);
}
