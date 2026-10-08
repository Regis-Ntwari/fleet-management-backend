package com.limoz.fleet.telematics.movement.dto;

import com.limoz.fleet.telematics.movement.MovementDataSource;
import com.limoz.fleet.telematics.movement.MovementFlag;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Filters of the daily movement list. {@code date} selects one day; {@code from}/{@code to} a range.
 * Without any of them the list shows yesterday.
 */
public record DailyMovementFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        Long vehicleId,
        MovementFlag flag,
        Long categoryId,
        Boolean moved,
        MovementDataSource dataSource) {}
