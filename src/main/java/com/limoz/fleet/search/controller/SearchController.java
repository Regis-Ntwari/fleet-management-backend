package com.limoz.fleet.search.controller;

import com.limoz.fleet.search.service.GlobalSearchService;

import com.limoz.fleet.search.dto.GlobalSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Global search across vehicles, drivers, clients, bookings, trips, maintenance jobs and incidents")
public class SearchController {

    private final GlobalSearchService searchService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Global search (minimum 2 characters; results limited per group and filtered by the caller's permissions)")
    public GlobalSearchResponse search(@RequestParam("q") String query, @RequestParam(defaultValue = "5") int limit) {
        return searchService.search(query, Math.min(Math.max(limit, 1), 20));
    }
}
