package com.limoz.fleet.incident;

public enum IncidentStatus {
    OPEN, UNDER_INVESTIGATION, RESOLVED, CLOSED;

    /** Explicit lifecycle: OPEN -> UNDER_INVESTIGATION -> RESOLVED -> CLOSED, reopening back to investigation. */
    public boolean canTransitionTo(IncidentStatus to) {
        return switch (this) {
            case OPEN -> to == UNDER_INVESTIGATION || to == RESOLVED;
            case UNDER_INVESTIGATION -> to == RESOLVED;
            case RESOLVED -> to == CLOSED || to == UNDER_INVESTIGATION;
            case CLOSED -> to == UNDER_INVESTIGATION;
        };
    }

    public boolean isOpen() {
        return this == OPEN || this == UNDER_INVESTIGATION;
    }
}
