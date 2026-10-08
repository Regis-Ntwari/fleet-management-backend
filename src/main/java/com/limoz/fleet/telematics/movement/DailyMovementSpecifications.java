package com.limoz.fleet.telematics.movement;

import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.telematics.movement.dto.DailyMovementFilter;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

final class DailyMovementSpecifications {

    private DailyMovementSpecifications() {}

    static Specification<DailyMovementSummary> from(DailyMovementFilter f, LocalDate defaultDate) {
        LocalDate from = f.date() != null ? f.date() : f.from();
        LocalDate to = f.date() != null ? f.date() : f.to();
        if (from == null && to == null) {
            from = defaultDate;
            to = defaultDate;
        }
        return Specifications.and(
                Specifications.isFalse("vehicle.archived"),
                Specifications.dateBetween("summaryDate", from, to),
                Specifications.equal("vehicle.id", f.vehicleId()),
                Specifications.equal("vehicle.category.id", f.categoryId()),
                Specifications.equal("moved", f.moved()),
                Specifications.equal("dataSource", f.dataSource()),
                hasFlag(f.flag()));
    }

    /** JSONB containment: {@code flags ? 'FLAG'} expressed through the underlying {@code jsonb_exists} function. */
    static Specification<DailyMovementSummary> hasFlag(MovementFlag flag) {
        if (flag == null) return null;
        return (root, query, cb) -> cb.isTrue(cb.function("jsonb_exists", Boolean.class, root.get("flags"), cb.literal(flag.name())));
    }
}
