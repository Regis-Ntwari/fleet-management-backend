package com.limoz.fleet.common.sequence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReferenceSequenceRepository extends JpaRepository<ReferenceSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ReferenceSequence s where s.sequenceKey = :key")
    Optional<ReferenceSequence> lockByKey(@Param("key") String key);
}
