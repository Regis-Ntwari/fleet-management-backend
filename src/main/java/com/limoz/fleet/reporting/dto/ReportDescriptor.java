package com.limoz.fleet.reporting.dto;

import java.util.List;

public record ReportDescriptor(String code, String title, String description, List<String> formats, String additionalPermission) {}
