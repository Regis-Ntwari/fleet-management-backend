package com.limoz.fleet.customer.dto;

import com.limoz.fleet.customer.CommitmentStatus;

import java.util.List;

public record CommitmentFilter(String q, Long customerId, List<CommitmentStatus> status) {}
