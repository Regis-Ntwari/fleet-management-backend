package com.limoz.fleet.driver;

import com.limoz.fleet.audit.AuditAction;
import com.limoz.fleet.audit.AuditService;
import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.common.exception.BusinessRuleException;
import com.limoz.fleet.common.exception.DuplicateResourceException;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.common.sequence.ReferenceNumberService;
import com.limoz.fleet.common.sequence.ReferenceType;
import com.limoz.fleet.common.util.Specifications;
import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.driver.dto.DriverFilter;
import com.limoz.fleet.driver.dto.DriverRequest;
import com.limoz.fleet.driver.dto.DriverResponse;
import com.limoz.fleet.driver.dto.DriverStatusChangeRequest;
import com.limoz.fleet.driver.dto.DriverSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class DriverService {

    private final DriverRepository driverRepository;
    private final DriverMapper mapper;
    private final ReferenceNumberService referenceNumberService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<DriverResponse> search(DriverFilter f, Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        Specification<Driver> spec = Specifications.and(
                Boolean.TRUE.equals(f.archived()) ? null : Specifications.isFalse("archived"),
                Specifications.likeAny(f.q(), "firstName", "lastName", "licenseNumber", "phone", "driverCode", "nationalId"),
                Specifications.in("status", f.status()),
                Specifications.equal("employmentStatus", f.employmentStatus()),
                Specifications.equal("basedIn", f.basedIn()),
                f.licenseExpired() == null ? null : (root, q, cb) -> Boolean.TRUE.equals(f.licenseExpired())
                        ? cb.lessThan(root.get("licenseExpiryDate"), today)
                        : cb.or(cb.isNull(root.get("licenseExpiryDate")), cb.greaterThanOrEqualTo(root.get("licenseExpiryDate"), today)));
        return PageResponse.from(driverRepository.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public DriverResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public List<DriverSummary> summaries(List<DriverStatus> statuses) {
        List<Driver> drivers = statuses == null || statuses.isEmpty()
                ? driverRepository.findByArchivedFalseOrderByLastNameAscFirstNameAsc()
                : driverRepository.findByArchivedFalseAndStatusInOrderByLastNameAscFirstNameAsc(statuses);
        return drivers.stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public Driver load(Long id) {
        return driverRepository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Driver", id));
    }

    @Transactional(readOnly = true)
    public Driver loadActive(Long id) {
        Driver driver = load(id);
        if (driver.isArchived()) {
            throw new BusinessRuleException("DRIVER_ARCHIVED", "Driver " + driver.getFullName() + " is archived");
        }
        return driver;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public DriverResponse create(DriverRequest request) {
        validateUniqueness(request, null);
        validateDates(request);
        Driver driver = new Driver();
        driver.setDriverCode(referenceNumberService.next(ReferenceType.DRIVER));
        apply(driver, request);
        DriverResponse response = mapper.toResponse(driverRepository.save(driver));
        auditService.record(AuditAction.CREATE, "Driver", driver.getId(), driver.getDriverCode(), null, response, "Driver created");
        return response;
    }

    public DriverResponse update(Long id, DriverRequest request) {
        Driver driver = load(id);
        validateUniqueness(request, id);
        validateDates(request);
        DriverResponse before = mapper.toResponse(driver);
        apply(driver, request);
        DriverResponse after = mapper.toResponse(driverRepository.save(driver));
        auditService.record(AuditAction.UPDATE, "Driver", id, driver.getDriverCode(), before, after, "Driver updated");
        return after;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public DriverResponse changeStatus(Long id, DriverStatusChangeRequest request) {
        Driver driver = load(id);
        DriverStatus from = driver.getStatus();
        DriverStatus to = request.status();
        if (from == to) return mapper.toResponse(driver);
        if (from == DriverStatus.ON_TRIP) {
            throw new BusinessRuleException("STATUS_MANAGED_BY_WORKFLOW", "Driver is on a trip; complete the trip first");
        }
        if (to == DriverStatus.ON_TRIP || to == DriverStatus.ASSIGNED) {
            throw new BusinessRuleException("STATUS_MANAGED_BY_WORKFLOW", "Status " + to + " is set automatically by assignments and trips");
        }
        if (to == DriverStatus.AVAILABLE && driver.getCurrentVehicle() != null) {
            to = DriverStatus.ASSIGNED;
        }
        transition(driver, to, request.reason());
        return mapper.toResponse(driverRepository.save(driver));
    }

    /** Workflow-driven status change used by assignments, trips and dispatch. */
    public void transition(Driver driver, DriverStatus to, String reason) {
        DriverStatus from = driver.getStatus();
        if (from == to) return;
        driver.setStatus(to);
        auditService.record(AuditAction.STATUS_CHANGE, "Driver", driver.getId(), driver.getDriverCode(),
                Map.of("status", from), Map.of("status", to), "Driver status " + from + " -> " + to + (reason == null ? "" : ": " + reason));
    }

    public DriverStatus restingStatus(Driver driver) {
        return driver.getCurrentVehicle() != null ? DriverStatus.ASSIGNED : DriverStatus.AVAILABLE;
    }

    @CacheEvict(cacheNames = CacheConfig.DASHBOARD, allEntries = true)
    public void archive(Long id) {
        Driver driver = load(id);
        if (driver.getStatus() == DriverStatus.ON_TRIP) {
            throw new BusinessRuleException("Driver cannot be archived while on a trip");
        }
        if (driver.getCurrentVehicle() != null) {
            throw new BusinessRuleException("End the driver's active vehicle assignment before archiving");
        }
        DriverResponse before = mapper.toResponse(driver);
        driver.setArchived(true);
        driver.setArchivedAt(Instant.now(clock));
        driver.setStatus(DriverStatus.INACTIVE);
        driverRepository.save(driver);
        auditService.record(AuditAction.ARCHIVE, "Driver", id, driver.getDriverCode(), before, mapper.toResponse(driver), "Driver archived");
    }

    public DriverResponse restore(Long id) {
        Driver driver = load(id);
        if (!driver.isArchived()) {
            throw new BusinessRuleException("Driver is not archived");
        }
        driver.setArchived(false);
        driver.setArchivedAt(null);
        driver.setStatus(DriverStatus.AVAILABLE);
        DriverResponse after = mapper.toResponse(driverRepository.save(driver));
        auditService.record(AuditAction.RESTORE, "Driver", id, driver.getDriverCode(), null, after, "Driver restored");
        return after;
    }

    private void validateUniqueness(DriverRequest r, Long excludeId) {
        if (driverRepository.licenseExists(r.licenseNumber().trim(), excludeId)) {
            throw new DuplicateResourceException("A driver with licence " + r.licenseNumber() + " already exists");
        }
        if (r.nationalId() != null && !r.nationalId().isBlank() && driverRepository.nationalIdExists(r.nationalId().trim(), excludeId)) {
            throw new DuplicateResourceException("A driver with national ID " + r.nationalId() + " already exists");
        }
    }

    private void validateDates(DriverRequest r) {
        if (r.licenseIssueDate() != null && r.licenseExpiryDate() != null && r.licenseIssueDate().isAfter(r.licenseExpiryDate())) {
            throw new BusinessRuleException("Licence issue date cannot be after its expiry date");
        }
    }

    private void apply(Driver d, DriverRequest r) {
        d.setFirstName(r.firstName().trim());
        d.setLastName(r.lastName().trim());
        d.setPhone(r.phone());
        d.setEmail(r.email() == null ? null : r.email().trim().toLowerCase());
        d.setNationalId(r.nationalId() == null || r.nationalId().isBlank() ? null : r.nationalId().trim());
        d.setLicenseNumber(r.licenseNumber().trim().toUpperCase());
        d.setLicenseCategory(r.licenseCategory());
        d.setLicenseIssueDate(r.licenseIssueDate());
        d.setLicenseExpiryDate(r.licenseExpiryDate());
        d.setEmploymentStatus(r.employmentStatus() == null ? EmploymentStatus.FULL_TIME : r.employmentStatus());
        d.setBasedIn(r.basedIn());
        d.setEmergencyContactName(r.emergencyContactName());
        d.setEmergencyContactPhone(r.emergencyContactPhone());
        d.setJoiningDate(r.joiningDate());
        d.setDateOfBirth(r.dateOfBirth());
        d.setPhotoAttachmentId(r.photoAttachmentId());
        d.setNotes(r.notes());
    }
}
