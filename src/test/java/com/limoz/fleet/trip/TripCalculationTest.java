package com.limoz.fleet.trip;

import com.limoz.fleet.trip.domain.TripCalculations;
import com.limoz.fleet.trip.domain.TripStatus;

import com.limoz.fleet.common.exception.BusinessRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripCalculationTest {

    @Test
    @DisplayName("distance is end minus start odometer with one decimal")
    void distance() {
        assertThat(TripCalculations.distanceKm(15000, 15250)).isEqualByComparingTo("250.0");
        assertThat(TripCalculations.distanceKm(15000, 15000)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(TripCalculations.distanceKm(15000, 15250).scale()).isEqualTo(1);
    }

    @Test
    @DisplayName("an end odometer below the start reading is rejected with END_ODOMETER_BELOW_START")
    void distanceRejectsDecrease() {
        assertThatThrownBy(() -> TripCalculations.distanceKm(15250, 15000))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getCode()).isEqualTo("END_ODOMETER_BELOW_START");
    }

    @Test
    @DisplayName("duration is the whole number of minutes between start and end")
    void duration() {
        Instant start = Instant.parse("2026-06-10T06:00:00Z");
        assertThat(TripCalculations.durationMinutes(start, start.plusSeconds(3 * 3600 + 25 * 60 + 59))).isEqualTo(205);
        assertThat(TripCalculations.durationMinutes(start, start)).isZero();
        assertThatThrownBy(() -> TripCalculations.durationMinutes(start, start.minusSeconds(1)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).getCode()).isEqualTo("END_BEFORE_START");
    }

    @Test
    @DisplayName("trip status machine: PLANNED -> DISPATCHED -> IN_PROGRESS -> COMPLETED, cancel from any open status")
    void statusMachine() {
        assertThat(TripStatus.PLANNED.canTransitionTo(TripStatus.DISPATCHED)).isTrue();
        assertThat(TripStatus.PLANNED.canTransitionTo(TripStatus.IN_PROGRESS)).isTrue();
        assertThat(TripStatus.PLANNED.canTransitionTo(TripStatus.COMPLETED)).isFalse();
        assertThat(TripStatus.DISPATCHED.canTransitionTo(TripStatus.IN_PROGRESS)).isTrue();
        assertThat(TripStatus.DISPATCHED.canTransitionTo(TripStatus.PLANNED)).isFalse();
        assertThat(TripStatus.IN_PROGRESS.canTransitionTo(TripStatus.COMPLETED)).isTrue();
        assertThat(TripStatus.IN_PROGRESS.canTransitionTo(TripStatus.DISPATCHED)).isFalse();
        for (TripStatus open : new TripStatus[]{TripStatus.PLANNED, TripStatus.DISPATCHED, TripStatus.IN_PROGRESS}) {
            assertThat(open.canTransitionTo(TripStatus.CANCELLED)).isTrue();
            assertThat(open.isActive()).isTrue();
        }
        for (TripStatus closed : new TripStatus[]{TripStatus.COMPLETED, TripStatus.CANCELLED}) {
            for (TripStatus to : TripStatus.values()) {
                assertThat(closed.canTransitionTo(to)).isFalse();
            }
            assertThat(closed.isActive()).isFalse();
        }
    }
}
