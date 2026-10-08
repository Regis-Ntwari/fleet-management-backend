package com.limoz.fleet.common;

import com.limoz.fleet.common.sequence.ReferenceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceTypeTest {

    @Test
    void formatsMatchTheReferenceApplication() {
        assertThat(ReferenceType.BOOKING.format(2026, 12)).isEqualTo("BK-2026-0012");
        assertThat(ReferenceType.COMMITMENT.format(2026, 42)).isEqualTo("CMT-0042");
        assertThat(ReferenceType.PURCHASE_ORDER.format(2026, 188)).isEqualTo("LPO-2026-0188");
        assertThat(ReferenceType.MAINTENANCE.format(2026, 46)).isEqualTo("MNT-2026-0046");
        assertThat(ReferenceType.PAYMENT.format(2026, 3301)).isEqualTo("PAY-3301");
        assertThat(ReferenceType.DEPLOYMENT_VOUCHER.format(2026, 628)).isEqualTo("LIMOZ/000628/2026");
        assertThat(ReferenceType.GARAGE_INTAKE.format(2026, 114)).isEqualTo("GRG/000114/2026");
        assertThat(ReferenceType.TRIP.format(2026, 7)).isEqualTo("TRP-2026-00007");
    }

    @Test
    void yearlySequencesUseAYearScopedKey() {
        assertThat(ReferenceType.BOOKING.sequenceKey(2026)).isEqualTo("BK-2026");
        assertThat(ReferenceType.PAYMENT.sequenceKey(2026)).isEqualTo("PAY");
    }
}
