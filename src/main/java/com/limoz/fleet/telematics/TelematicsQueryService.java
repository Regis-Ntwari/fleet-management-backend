package com.limoz.fleet.telematics;

import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.telematics.dto.LatestPositionResponse;
import com.limoz.fleet.telematics.dto.PositionSeriesResponse;
import com.limoz.fleet.vehicle.Vehicle;
import com.limoz.fleet.vehicle.VehicleRepository;
import com.limoz.fleet.vehicle.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read side of telematics: position history and the live "latest position per vehicle" view. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TelematicsQueryService {

    public static final int MAX_SERIES_ROWS = 5000;
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);
    private static final Duration MAX_WINDOW = Duration.ofDays(31);

    private final VehiclePositionRepository positionRepository;
    private final TelematicsDeviceRepository deviceRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleService vehicleService;
    private final TelematicsMapper mapper;
    private final Clock clock;

    public PositionSeriesResponse positions(Long vehicleId, Instant from, Instant to) {
        Vehicle vehicle = vehicleService.load(vehicleId);
        Instant end = to == null ? Instant.now(clock) : to;
        Instant start = from == null ? end.minus(DEFAULT_WINDOW) : from;
        if (!start.isBefore(end)) {
            throw new BusinessRuleException("INVALID_RANGE", "'from' must be before 'to'");
        }
        if (Duration.between(start, end).compareTo(MAX_WINDOW) > 0) {
            throw new BusinessRuleException("RANGE_TOO_LARGE", "Position history can be queried for at most 31 days at a time");
        }
        List<VehiclePosition> rows = positionRepository.findInWindow(vehicleId, start, end, PageRequest.of(0, MAX_SERIES_ROWS));
        long total = rows.size() < MAX_SERIES_ROWS ? rows.size() : positionRepository.countInWindow(vehicleId, start, end);
        return new PositionSeriesResponse(vehicleId, vehicle.getPlateNumber(), start, end, rows.size(), total,
                total > rows.size(), rows.stream().map(mapper::toResponse).toList());
    }

    public List<LatestPositionResponse> latest() {
        List<VehiclePosition> latest = positionRepository.findLatestPerVehicle();
        if (latest.isEmpty()) {
            return List.of();
        }
        Map<Long, Vehicle> vehicles = vehicleRepository.findAllById(latest.stream().map(VehiclePosition::getVehicleId).toList())
                .stream().collect(Collectors.toMap(Vehicle::getId, Function.identity()));
        Map<Long, GpsStatus> gpsByVehicle = deviceRepository.findAllWithVehicle().stream()
                .collect(Collectors.toMap(d -> d.getVehicle().getId(), TelematicsDevice::getGpsStatus, (a, b) -> a));
        return latest.stream()
                .filter(p -> vehicles.containsKey(p.getVehicleId()))
                .map(p -> mapper.toLatest(p, vehicles.get(p.getVehicleId()), gpsByVehicle.getOrDefault(p.getVehicleId(), GpsStatus.UNKNOWN)))
                .sorted(Comparator.comparing(LatestPositionResponse::plateNumber))
                .toList();
    }
}
