package com.limoz.fleet.customer.dto;

import jakarta.validation.constraints.Size;

public record ReasonRequest(@Size(max = 255) String reason) {}
