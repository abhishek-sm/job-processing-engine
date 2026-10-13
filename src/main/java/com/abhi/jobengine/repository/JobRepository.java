package com.abhi.jobengine.repository;

import com.abhi.jobengine.domain.Job;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    /** Tenant-scoped lookup, so one tenant cannot read another tenant's job by id. */
    Optional<Job> findByIdAndTenantId(UUID id, String tenantId);

    /** Finds a previous submission with the same idempotency key (backed by a unique index). */
    Optional<Job> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    /**
     * Loads a job with {@code SELECT ... FOR UPDATE}, so its status cannot change (for example
     * by a worker claiming it) until the current transaction ends. Must run in a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Job j where j.id = :id")
    Optional<Job> findByIdForUpdate(@Param("id") UUID id);
}
