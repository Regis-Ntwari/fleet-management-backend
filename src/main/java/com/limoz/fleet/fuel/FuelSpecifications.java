package com.limoz.fleet.fuel;

import com.limoz.fleet.common.util.DateRanges;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.fuel.dto.FuelFilter;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.ZoneId;

public final class FuelSpecifications {

    private FuelSpecifications() {}

    public static Specification<FuelTransaction> from(FuelFilter f, ZoneId zone) {
        Instant from = f.from() == null ? null : DateRanges.forDate(f.from(), zone).from();
        Instant toExclusive = f.to() == null ? null : DateRanges.forDate(f.to(), zone).to();
        return Specifications.and(
                Boolean.TRUE.equals(f.archived()) ? null : Specifications.isFalse("archived"),
                Specifications.likeAny(f.q(), "stationName", "receiptNumber", "vehicle.plateNumber", "supplierName"),
                Specifications.equal("vehicle.id", f.vehicleId()),
                Specifications.equal("driver.id", f.driverId()),
                Specifications.equal("fuelType", f.fuelType()),
                Specifications.equal("anomaly", f.anomaly()),
                f.station() == null || f.station().isBlank() ? null
                        : (root, q, cb) -> cb.equal(cb.lower(root.get("stationName")), f.station().trim().toLowerCase()),
                from == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("transactionAt"), from),
                toExclusive == null ? null : (root, q, cb) -> cb.lessThan(root.get("transactionAt"), toExclusive));
    }
}
