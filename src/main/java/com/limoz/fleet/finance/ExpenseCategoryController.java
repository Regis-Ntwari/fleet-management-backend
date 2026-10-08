package com.limoz.fleet.finance;

import com.limoz.fleet.finance.dto.ExpenseCategoryRequest;
import com.limoz.fleet.finance.dto.ExpenseCategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/expense-categories")
@RequiredArgsConstructor
@Tag(name = "Expense Categories", description = "Configurable expense categories (reference data)")
public class ExpenseCategoryController {

    private final ExpenseCategoryService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List expense categories")
    public List<ExpenseCategoryResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE','FINANCE_MANAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseCategoryResponse create(@Valid @RequestBody ExpenseCategoryRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE','FINANCE_MANAGE')")
    public ExpenseCategoryResponse update(@PathVariable Long id, @Valid @RequestBody ExpenseCategoryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an unused category")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
