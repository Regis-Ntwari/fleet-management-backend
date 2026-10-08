package com.limoz.fleet.incident;

/** Incident severity (named to avoid clashing with {@code common.event.Severity}). */
public enum IncidentSeverity {
    MINOR, MODERATE, MAJOR, CRITICAL;

    public boolean isSerious() {
        return this == MAJOR || this == CRITICAL;
    }
}
