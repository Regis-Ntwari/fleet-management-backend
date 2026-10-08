package com.limoz.fleet.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Append-only investigation history of an incident (notes, status changes, attachments). Never deleted. */
@Entity
@Table(name = "incident_updates")
@Getter
@Setter
@NoArgsConstructor
public class IncidentUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(name = "update_type", nullable = false, length = 20)
    private IncidentUpdateType updateType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private IncidentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 20)
    private IncidentStatus toStatus;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "author_name", length = 150)
    private String authorName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
