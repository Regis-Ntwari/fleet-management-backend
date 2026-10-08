package com.limoz.fleet.search.dto;

import java.util.List;
import java.util.Map;

public record GlobalSearchResponse(String query, int total, Map<String, List<SearchResult>> groups) {}
