package com.limoz.fleet.reporting.export.service;

import com.limoz.fleet.reporting.export.domain.ReportFormat;
import com.limoz.fleet.reporting.export.domain.ReportTable;

public interface ReportExporter {

    ReportFormat format();

    byte[] export(ReportTable table);
}
