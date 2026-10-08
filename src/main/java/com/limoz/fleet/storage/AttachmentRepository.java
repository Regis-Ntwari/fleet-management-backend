package com.limoz.fleet.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByOwnerTypeAndOwnerIdOrderByCreatedAtDesc(String ownerType, Long ownerId);
}
