package com.limoz.fleet.driver;

import com.limoz.fleet.common.api.PageResponse;
import com.limoz.fleet.driver.dto.DriverFilter;
import com.limoz.fleet.driver.dto.DriverRequest;
import com.limoz.fleet.driver.dto.DriverResponse;
import com.limoz.fleet.driver.dto.DriverStatusChangeRequest;
import com.limoz.fleet.driver.dto.DriverSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
@Tag(name = "Drivers", description = "Driver roster, licences and availability")
public class DriverController {

    private final DriverService driverService;

    @GetMapping
    @PreAuthorize("hasAuthority('DRIVER_READ')")
    @Operation(summary = "Search drivers (paginated)")
    public PageResponse<DriverResponse> search(@ParameterObject DriverFilter filter,
                                               @ParameterObject @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {
        return driverService.search(filter, pageable);
    }

    @GetMapping("/summaries")
    @PreAuthorize("hasAuthority('DRIVER_READ')")
    @Operation(summary = "Lightweight list for dropdowns")
    public List<DriverSummary> summaries(@RequestParam(required = false) List<DriverStatus> status) {
        return driverService.summaries(status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DRIVER_READ')")
    @Operation(summary = "Driver profile")
    public DriverResponse get(@PathVariable Long id) {
        return driverService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('DRIVER_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a driver")
    public DriverResponse create(@Valid @RequestBody DriverRequest request) {
        return driverService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('DRIVER_MANAGE')")
    @Operation(summary = "Update a driver")
    public DriverResponse update(@PathVariable Long id, @Valid @RequestBody DriverRequest request) {
        return driverService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('DRIVER_MANAGE')")
    @Operation(summary = "Change availability (OFF_DUTY, ON_LEAVE, SUSPENDED, AVAILABLE, INACTIVE)")
    public DriverResponse changeStatus(@PathVariable Long id, @Valid @RequestBody DriverStatusChangeRequest request) {
        return driverService.changeStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DRIVER_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive a driver (soft delete)")
    public void archive(@PathVariable Long id) {
        driverService.archive(id);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('DRIVER_MANAGE')")
    @Operation(summary = "Restore an archived driver")
    public DriverResponse restore(@PathVariable Long id) {
        return driverService.restore(id);
    }
}
