package com.limoz.fleet.search;

import com.limoz.fleet.search.dto.SearchResult;

import java.util.List;

/**
 * Modules contribute to the global search by implementing this interface (vehicles, drivers, clients, bookings,
 * trips, maintenance jobs, incidents...). Each source runs a bounded database query - never loads full tables.
 */
public interface SearchSource {

    /** Group key shown in the UI, e.g. "vehicles". */
    String group();

    /** Permission required to see this group's results. */
    String requiredPermission();

    List<SearchResult> search(String query, int limit);
}
