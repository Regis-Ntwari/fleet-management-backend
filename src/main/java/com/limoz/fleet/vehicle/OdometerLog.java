package com.limoz.fleet.vehicle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "odometer_logs")
@Getter
@Setter
@NoArgsConstructor
public class OdometerLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "reading_km", nullable = false)
    private long readingKm;

    @Column(name = "previous_km")
    private Long previousKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OdometerSource source;

    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "correction_reason", length = 255)
    private String correctionReason;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by", length = 150)
    private String recordedBy;
}
