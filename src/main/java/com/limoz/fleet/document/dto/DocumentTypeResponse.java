package com.limoz.fleet.document.dto;

import com.limoz.fleet.document.domain.DocumentAppliesTo;

public record DocumentTypeResponse(Long id, String code, String name, DocumentAppliesTo appliesTo, boolean requiredForDispatch,
                                   Integer warningDays, boolean active, int sortOrder) {}
