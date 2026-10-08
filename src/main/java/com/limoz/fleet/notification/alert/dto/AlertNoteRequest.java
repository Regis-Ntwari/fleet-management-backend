package com.limoz.fleet.notification.alert.dto;

import jakarta.validation.constraints.Size;

public record AlertNoteRequest(@Size(max = 255) String note) {}
