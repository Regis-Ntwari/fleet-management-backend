package com.limoz.fleet.incident.dto;

import com.limoz.fleet.incident.domain.FineStatus;

import java.time.LocalDate;
import java.util.List;

public record TrafficFineFilter(String q, Long vehicleId, Long driverId, List<FineStatus> status, LocalDate from, LocalDate to) {}
