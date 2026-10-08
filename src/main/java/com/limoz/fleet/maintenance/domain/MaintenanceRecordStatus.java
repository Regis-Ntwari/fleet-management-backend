package com.limoz.fleet.maintenance.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Unified lifecycle of a maintenance job (simple MNT flow and garage intake flow).
 * <pre>
 * REPORTED -> INSPECTION -> APPROVED -> IN_PROGRESS <-> WAITING_FOR_PARTS -> COMPLETED -> RELEASED
 * </pre>
 * REPORTED and INSPECTION may also go straight to IN_PROGRESS; any non-terminal status may be CANCELLED.
 */
public enum MaintenanceRecordStatus {
    REPORTED, INSPECTION, APPROVED, IN_PROGRESS, WAITING_FOR_PARTS, COMPLETED, RELEASED, CANCELLED;

    public static final Set<MaintenanceRecordStatus> IN_WORKSHOP = EnumSet.of(IN_PROGRESS, WAITING_FOR_PARTS);
    public static final Set<MaintenanceRecordStatus> TERMINAL = EnumSet.of(RELEASED, CANCELLED);
    public static final Set<MaintenanceRecordStatus> OPEN = EnumSet.of(REPORTED, INSPECTION, APPROVED, IN_PROGRESS, WAITING_FOR_PARTS);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** True while tasks, parts and basic details may still be edited. */
    public boolean isOpen() {
        return OPEN.contains(this);
    }

    public boolean isInWorkshop() {
        return IN_WORKSHOP.contains(this);
    }

    public boolean canTransitionTo(MaintenanceRecordStatus to) {
        if (to == CANCELLED) return !isTerminal();
        return switch (this) {
            case REPORTED -> to == INSPECTION || to == APPROVED || to == IN_PROGRESS;
            case INSPECTION -> to == APPROVED || to == IN_PROGRESS;
            case APPROVED -> to == IN_PROGRESS;
            case IN_PROGRESS -> to == WAITING_FOR_PARTS || to == COMPLETED;
            case WAITING_FOR_PARTS -> to == IN_PROGRESS || to == COMPLETED;
            case COMPLETED -> to == RELEASED;
            case RELEASED, CANCELLED -> false;
        };
    }
}
