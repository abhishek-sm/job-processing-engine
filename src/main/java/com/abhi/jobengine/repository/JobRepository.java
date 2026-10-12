package com.abhi.jobengine.repository;

import com.abhi.jobengine.domain.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    /** Tenant-scoped lookup, so one tenant cannot read another tenant's job by id. */
    Optional<Job> findByIdAndTenantId(UUID id, String tenantId);

    /** Finds a previous submission with the same idempotency key (backed by a unique index). */
    Optional<Job> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
}
