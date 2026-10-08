package com.limoz.fleet.common.sequence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Generates gap-free business reference numbers. The sequence row is locked for the duration of the
 * calling transaction, which serialises creation of records of the same type - acceptable for
 * operational volumes and guarantees uniqueness without relying on retries.
 */
@Service
@RequiredArgsConstructor
public class ReferenceNumberService {

    private final ReferenceSequenceRepository repository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(ReferenceType type) {
        int year = LocalDate.now(clock).getYear();
        String key = type.sequenceKey(year);
        ReferenceSequence sequence = repository.lockByKey(key).orElseGet(() -> repository.saveAndFlush(new ReferenceSequence(key, 1)));
        long value = sequence.getNextValue();
        sequence.setNextValue(value + 1);
        repository.save(sequence);
        return type.format(year, value);
    }
}
