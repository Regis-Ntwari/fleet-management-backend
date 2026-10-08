package com.limoz.fleet.document;

import com.limoz.fleet.document.domain.DocumentStatus;
import com.limoz.fleet.document.domain.DocumentStatusCalculator;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentStatusCalculatorTest {

    private final LocalDate today = LocalDate.of(2026, 6, 4);

    @Test
    void expiredWhenExpiryIsBeforeToday() {
        assertThat(DocumentStatusCalculator.compute(today.minusDays(1), today, 30)).isEqualTo(DocumentStatus.EXPIRED);
    }

    @Test
    void validOnExpiryDayItself() {
        assertThat(DocumentStatusCalculator.compute(today, today, 0)).isEqualTo(DocumentStatus.EXPIRING_SOON);
        assertThat(DocumentStatusCalculator.compute(today.plusDays(31), today, 30)).isEqualTo(DocumentStatus.VALID);
    }

    @Test
    void expiringSoonInsideWarningWindow() {
        assertThat(DocumentStatusCalculator.compute(today.plusDays(30), today, 30)).isEqualTo(DocumentStatus.EXPIRING_SOON);
        assertThat(DocumentStatusCalculator.compute(today.plusDays(7), today, 15)).isEqualTo(DocumentStatus.EXPIRING_SOON);
    }

    @Test
    void notApplicableWithoutExpiry() {
        assertThat(DocumentStatusCalculator.compute(null, today, 30)).isEqualTo(DocumentStatus.NOT_APPLICABLE);
    }
}
