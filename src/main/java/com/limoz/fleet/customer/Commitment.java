package com.limoz.fleet.customer;

import com.limoz.fleet.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Framework contract (CMT-0042) that bookings and LPOs draw down against. */
@Entity
@Table(name = "commitments")
@Getter
@Setter
@NoArgsConstructor
public class Commitment extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "contracted_value", nullable = false, precision = 16, scale = 2)
    private BigDecimal contractedValue = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "RWF";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommitmentStatus status = CommitmentStatus.DRAFT;

    @Column(name = "attachment_id")
    private Long attachmentId;

    @Column(columnDefinition = "text")
    private String notes;
}
