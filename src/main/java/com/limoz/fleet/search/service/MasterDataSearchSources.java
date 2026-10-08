package com.limoz.fleet.search.service;

import com.limoz.fleet.search.domain.SearchSource;

import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.customer.domain.Customer;
import com.limoz.fleet.customer.repository.CustomerRepository;
import com.limoz.fleet.driver.domain.Driver;
import com.limoz.fleet.driver.repository.DriverRepository;
import com.limoz.fleet.search.dto.SearchResult;
import com.limoz.fleet.security.Permissions;
import com.limoz.fleet.vehicle.domain.Vehicle;
import com.limoz.fleet.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/** Search sources for vehicles, drivers and clients. Other modules register their own SearchSource beans. */
@Configuration
@RequiredArgsConstructor
public class MasterDataSearchSources {

    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final CustomerRepository customerRepository;

    @Bean
    SearchSource vehicleSearchSource() {
        return new SearchSource() {
            public String group() { return "vehicles"; }
            public String requiredPermission() { return Permissions.VEHICLE_READ; }
            public List<SearchResult> search(String q, int limit) {
                Specification<Vehicle> spec = Specifications.and(Specifications.isFalse("archived"),
                        Specifications.likeAny(q, "plateNumber", "fleetNumber", "make", "model", "chassisNumber"));
                return vehicleRepository.findAll(spec, PageRequest.of(0, limit, Sort.by("plateNumber"))).stream()
                        .map(v -> new SearchResult("VEHICLE", v.getId(), v.getPlateNumber(), v.getPlateNumber(),
                                v.getMake() + " " + v.getModel() + " · " + v.getCategory().getName(),
                                v.getOperationalStatus().name(), "/vehicles/" + v.getId()))
                        .toList();
            }
        };
    }

    @Bean
    SearchSource driverSearchSource() {
        return new SearchSource() {
            public String group() { return "drivers"; }
            public String requiredPermission() { return Permissions.DRIVER_READ; }
            public List<SearchResult> search(String q, int limit) {
                Specification<Driver> spec = Specifications.and(Specifications.isFalse("archived"),
                        Specifications.likeAny(q, "firstName", "lastName", "licenseNumber", "phone", "driverCode"));
                return driverRepository.findAll(spec, PageRequest.of(0, limit, Sort.by("lastName"))).stream()
                        .map(d -> new SearchResult("DRIVER", d.getId(), d.getDriverCode(), d.getFullName(),
                                d.getLicenseNumber() + (d.getPhone() == null ? "" : " · " + d.getPhone()),
                                d.getStatus().name(), "/drivers/" + d.getId()))
                        .toList();
            }
        };
    }

    @Bean
    SearchSource customerSearchSource() {
        return new SearchSource() {
            public String group() { return "clients"; }
            public String requiredPermission() { return Permissions.CUSTOMER_READ; }
            public List<SearchResult> search(String q, int limit) {
                Specification<Customer> spec = Specifications.likeAny(q, "name", "tin", "customerCode", "contactPerson");
                return customerRepository.findAll(spec, PageRequest.of(0, limit, Sort.by("name"))).stream()
                        .map(c -> new SearchResult("CUSTOMER", c.getId(), c.getCustomerCode(), c.getName(),
                                c.getTin() == null ? c.getCustomerType().name() : "TIN " + c.getTin(),
                                c.isActive() ? "ACTIVE" : "INACTIVE", "/clients/" + c.getId()))
                        .toList();
            }
        };
    }
}
