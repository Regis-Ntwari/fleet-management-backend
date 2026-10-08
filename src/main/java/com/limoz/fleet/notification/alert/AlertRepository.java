package com.limoz.fleet.notification.alert;

import com.limoz.fleet.notification.NotificationSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findByDedupeKey(String dedupeKey);

    List<Alert> findByDedupeKeyIn(Collection<String> dedupeKeys);

    List<Alert> findByStatusIn(Collection<AlertStatus> statuses);

    /** Alert centre listing: open before resolved, critical before warning before info, most recently detected first. */
    @Query(value = "select a from Alert a where (:status is null or a.status = :status) and (:severity is null or a.severity = :severity) "
            + "and (:type is null or a.type = :type) and (:entityType is null or a.entityType = :entityType) "
            + "order by case a.status when com.limoz.fleet.notification.alert.AlertStatus.ACTIVE then 0 "
            + "when com.limoz.fleet.notification.alert.AlertStatus.ACKNOWLEDGED then 1 else 2 end, "
            + "case a.severity when com.limoz.fleet.notification.NotificationSeverity.CRITICAL then 0 "
            + "when com.limoz.fleet.notification.NotificationSeverity.WARNING then 1 else 2 end, a.lastDetectedAt desc, a.id desc",
            countQuery = "select count(a) from Alert a where (:status is null or a.status = :status) and (:severity is null or a.severity = :severity) "
                    + "and (:type is null or a.type = :type) and (:entityType is null or a.entityType = :entityType)")
    Page<Alert> search(@Param("status") AlertStatus status, @Param("severity") NotificationSeverity severity,
                       @Param("type") AlertType type, @Param("entityType") String entityType, Pageable pageable);

    @Query("select a.severity as severity, count(a) as total from Alert a where a.status in :statuses group by a.severity")
    List<SeverityCount> countBySeverity(@Param("statuses") Collection<AlertStatus> statuses);

    @Query("select a.status as status, count(a) as total from Alert a group by a.status")
    List<StatusCount> countByStatus();

    @Query("select a.type as type, count(a) as total from Alert a where a.status in :statuses group by a.type order by count(a) desc, a.type asc")
    List<TypeCount> countByType(@Param("statuses") Collection<AlertStatus> statuses, Pageable pageable);

    interface SeverityCount {
        NotificationSeverity getSeverity();
        long getTotal();
    }

    interface StatusCount {
        AlertStatus getStatus();
        long getTotal();
    }

    interface TypeCount {
        AlertType getType();
        long getTotal();
    }
}
