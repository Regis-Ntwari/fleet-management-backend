package com.limoz.fleet.search.dto;

/** One hit of the global search: what it is, how to display it, and where it lives in the frontend. */
public record SearchResult(String type, Long id, String reference, String title, String subtitle, String status, String linkPath) {}
