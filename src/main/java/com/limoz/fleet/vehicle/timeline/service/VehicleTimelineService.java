package com.limoz.fleet.vehicle.timeline.service;

import com.limoz.fleet.vehicle.timeline.domain.TimelineEntry;
import com.limoz.fleet.vehicle.timeline.domain.VehicleTimelineSource;

import com.limoz.fleet.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleTimelineService {

    private final VehicleService vehicleService;
    private final List<VehicleTimelineSource> sources;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TimelineEntry> timeline(Long vehicleId, Instant from, Instant to, int limit) {
        vehicleService.load(vehicleId);
        Instant end = to == null ? Instant.now(clock) : to;
        Instant start = from == null ? end.minus(Duration.ofDays(30)) : from;
        List<TimelineEntry> entries = new ArrayList<>();
        for (VehicleTimelineSource source : sources) {
            entries.addAll(source.entriesForVehicle(vehicleId, start, end));
        }
        entries.sort(Comparator.comparing(TimelineEntry::at).reversed());
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }
}
