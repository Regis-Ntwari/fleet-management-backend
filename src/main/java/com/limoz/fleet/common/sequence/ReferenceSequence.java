package com.limoz.fleet.common.sequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reference_sequences")
@Getter
@Setter
@NoArgsConstructor
public class ReferenceSequence {

    @Id
    @Column(name = "sequence_key", length = 60)
    private String sequenceKey;

    @Column(name = "next_value", nullable = false)
    private long nextValue;

    public ReferenceSequence(String sequenceKey, long nextValue) {
        this.sequenceKey = sequenceKey;
        this.nextValue = nextValue;
    }
}
