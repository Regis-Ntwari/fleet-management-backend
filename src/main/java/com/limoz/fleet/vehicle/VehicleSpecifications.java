package com.limoz.fleet.vehicle;

import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.vehicle.dto.VehicleFilter;
import org.springframework.data.jpa.domain.Specification;

public final class VehicleSpecifications {

    private VehicleSpecifications() {}

    public static Specification<Vehicle> from(VehicleFilter f) {
        boolean includeArchived = Boolean.TRUE.equals(f.archived());
        return Specifications.and(
                includeArchived ? null : Specifications.isFalse("archived"),
                Specifications.likeAny(f.q(), "plateNumber", "fleetNumber", "make", "model", "chassisNumber", "category.name"),
                Specifications.equal("category.id", f.categoryId()),
                Specifications.in("operationalStatus", f.status()),
                Specifications.equal("maintenanceStatus", f.maintenanceStatus()),
                Specifications.equal("fuelType", f.fuelType()),
                Specifications.equal("ownershipType", f.ownershipType()),
                Specifications.equal("department", f.department()),
                Specifications.equal("currentDriver.id", f.driverId()));
    }
}
