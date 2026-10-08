package com.limoz.fleet.maintenance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceScheduleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 8);
    private static final int DUE_SOON_KM = 500;
    private static final int DUE_SOON_DAYS = 14;

    @Test
    @DisplayName("next service point = last service + interval, per dimension")
    void nextValues() {
        ScheduleCalculator.Next next = ScheduleCalculator.next(15000L, LocalDate.of(2026, 1, 1), 5000, 180);
        assertThat(next.nextServiceOdometer()).isEqualTo(20000L);
        assertThat(next.nextServiceDate()).isEqualTo(LocalDate.of(2026, 6, 30));

        ScheduleCalculator.Next kmOnly = ScheduleCalculator.next(15000L, LocalDate.of(2026, 1, 1), 5000, null);
        assertThat(kmOnly.nextServiceOdometer()).isEqualTo(20000L);
        assertThat(kmOnly.nextServiceDate()).isNull();

        ScheduleCalculator.Next daysOnly = ScheduleCalculator.next(null, LocalDate.of(2026, 1, 1), 5000, 365);
        assertThat(daysOnly.nextServiceOdometer()).isNull();
        assertThat(daysOnly.nextServiceDate()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    @DisplayName("OK while both the odometer and the date are outside the warning windows")
    void ok() {
        assertThat(ScheduleCalculator.status(15000, TODAY, 20000L, TODAY.plusDays(60), DUE_SOON_KM, DUE_SOON_DAYS)).isEqualTo(ScheduleStatus.OK);
        assertThat(ScheduleCalculator.status(15000, TODAY, null, null, DUE_SOON_KM, DUE_SOON_DAYS)).as("nothing scheduled").isEqualTo(ScheduleStatus.OK);
        assertThat(ScheduleCalculator.kmRemaining(15000, 20000L)).isEqualTo(5000L);
        assertThat(ScheduleCalculator.daysRemaining(TODAY, TODAY.plusDays(60))).isEqualTo(60L);
    }

    @Test
    @DisplayName("DUE_SOON within the configured km or day window")
    void dueSoon() {
        assertThat(ScheduleCalculator.status(19500, TODAY, 20000L, TODAY.plusDays(60), DUE_SOON_KM, DUE_SOON_DAYS)).isEqualTo(ScheduleStatus.DUE_SOON);
        assertThat(ScheduleCalculator.status(19499, TODAY, 20000L, TODAY.plusDays(60), DUE_SOON_KM, DUE_SOON_DAYS)).isEqualTo(ScheduleStatus.OK);
        assertThat(ScheduleCalculator.status(15000, TODAY, 20000L, TODAY.plusDays(14), DUE_SOON_KM, DUE_SOON_DAYS)).isEqualTo(ScheduleStatus.DUE_SOON);
        assertThat(ScheduleCalculator.status(15000, TODAY, 20000L, TODAY.plusDays(15), DUE_SOON_KM, DUE_SOON_DAYS)).isEqualTo(ScheduleStatus.OK);
        assertThat(ScheduleCalculator.status(15000, TODAY, null, TODAY.plusDays(3), DUE_SOON_KM, DUE_SOON_DAYS)).as("date only").isEqualTo(ScheduleStatus.DUE_SOON);
    }

    @Test
    @DisplayName("OVERDUE once the odometer reaches the next service or the next date has arrived")
    void overdue() {
        assertThat(ScheduleCalculator.status(20000, TODAY, 20000L, TODAY.plusDays(60), DUE_SOON_KM, DUE_SOON_DAYS)).isEqualTo(ScheduleStatus.OVERDUE);
        assertThat(ScheduleCalculator.status(21000, TODAY, 20000L, null, DUE_SOON_KM, DUE_SOON_DAYS)).isEqualTo(ScheduleStatus.OVERDUE);
        assertThat(ScheduleCalculator.status(15000, TODAY, 20000L, TODAY, DUE_SOON_KM, DUE_SOON_DAYS)).isEqualTo(ScheduleStatus.OVERDUE);
        assertThat(ScheduleCalculator.status(15000, TODAY, 20000L, TODAY.minusDays(10), DUE_SOON_KM, DUE_SOON_DAYS)).isEqualTo(ScheduleStatus.OVERDUE);
        assertThat(ScheduleCalculator.status(21000, TODAY, 20000L, TODAY.plusDays(2), DUE_SOON_KM, DUE_SOON_DAYS))
                .as("overdue wins over due soon").isEqualTo(ScheduleStatus.OVERDUE);
    }

    @Test
    @DisplayName("lifecycle transitions are explicit")
    void transitions() {
        assertThat(MaintenanceRecordStatus.REPORTED.canTransitionTo(MaintenanceRecordStatus.INSPECTION)).isTrue();
        assertThat(MaintenanceRecordStatus.REPORTED.canTransitionTo(MaintenanceRecordStatus.COMPLETED)).isFalse();
        assertThat(MaintenanceRecordStatus.INSPECTION.canTransitionTo(MaintenanceRecordStatus.IN_PROGRESS)).isTrue();
        assertThat(MaintenanceRecordStatus.IN_PROGRESS.canTransitionTo(MaintenanceRecordStatus.WAITING_FOR_PARTS)).isTrue();
        assertThat(MaintenanceRecordStatus.WAITING_FOR_PARTS.canTransitionTo(MaintenanceRecordStatus.COMPLETED)).isTrue();
        assertThat(MaintenanceRecordStatus.COMPLETED.canTransitionTo(MaintenanceRecordStatus.RELEASED)).isTrue();
        assertThat(MaintenanceRecordStatus.COMPLETED.canTransitionTo(MaintenanceRecordStatus.IN_PROGRESS)).isFalse();
        assertThat(MaintenanceRecordStatus.RELEASED.canTransitionTo(MaintenanceRecordStatus.CANCELLED)).isFalse();
        assertThat(MaintenanceRecordStatus.CANCELLED.canTransitionTo(MaintenanceRecordStatus.REPORTED)).isFalse();
        assertThat(MaintenanceRecordStatus.APPROVED.canTransitionTo(MaintenanceRecordStatus.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("payment status follows the amount paid against the total")
    void paymentStatus() {
        assertThat(PaymentStatus.of(new java.math.BigDecimal("0"), new java.math.BigDecimal("100"))).isEqualTo(PaymentStatus.UNPAID);
        assertThat(PaymentStatus.of(new java.math.BigDecimal("40"), new java.math.BigDecimal("100"))).isEqualTo(PaymentStatus.PARTIAL);
        assertThat(PaymentStatus.of(new java.math.BigDecimal("100"), new java.math.BigDecimal("100"))).isEqualTo(PaymentStatus.PAID);
        assertThat(GarageDayBand.of(2, 3, 6)).isEqualTo(GarageDayBand.NORMAL);
        assertThat(GarageDayBand.of(3, 3, 6)).isEqualTo(GarageDayBand.AMBER);
        assertThat(GarageDayBand.of(6, 3, 6)).isEqualTo(GarageDayBand.RED);
    }
}
