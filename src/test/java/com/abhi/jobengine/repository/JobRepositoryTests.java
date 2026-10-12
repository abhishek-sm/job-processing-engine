package com.abhi.jobengine.repository;

import com.abhi.jobengine.domain.Job;
import com.abhi.jobengine.domain.JobStatus;
import com.abhi.jobengine.domain.JobType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Runs against the configured PostgreSQL (docker compose); each test rolls back.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JobRepositoryTests {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndLoadsJobWithJsonbPayloadAndDefaults() {
        Job saved = jobRepository.saveAndFlush(Job.builder()
                .tenantId("tenant-a")
                .jobType(JobType.GENERATE_REPORT)
                .payload(Map.of("reportId", 42, "formats", List.of("pdf", "csv"),
                        "filters", Map.of("region", "EU")))
                .build());
        // Force the next read to hit PostgreSQL instead of the persistence context.
        entityManager.clear();

        Job loaded = jobRepository.findByIdAndTenantId(saved.getId(), "tenant-a").orElseThrow();

        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getJobStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(loaded.getPayload())
                .containsEntry("reportId", 42)
                .containsEntry("formats", List.of("pdf", "csv"))
                .containsEntry("filters", Map.of("region", "EU"));
        assertThat(loaded.getPriority()).isEqualTo(Job.DEFAULT_PRIORITY);
        assertThat(loaded.getAttemptCount()).isZero();
        assertThat(loaded.getMaxAttempts()).isEqualTo(Job.DEFAULT_MAX_ATTEMPTS);
        assertThat(loaded.getFencingToken()).isZero();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isEqualTo(loaded.getCreatedAt());
        assertThat(loaded.getNextRunAt()).isEqualTo(loaded.getCreatedAt());
        assertThat(jobRepository.findByIdAndTenantId(saved.getId(), "tenant-b")).isEmpty();
    }

    @Test
    void rejectsDuplicateIdempotencyKeyWithinTenant() {
        jobRepository.saveAndFlush(jobWithKey("tenant-a", "key-1"));
        jobRepository.saveAndFlush(jobWithKey("tenant-b", "key-1"));

        assertThat(jobRepository.findByTenantIdAndIdempotencyKey("tenant-a", "key-1")).isPresent();
        assertThatThrownBy(() -> jobRepository.saveAndFlush(jobWithKey("tenant-a", "key-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Job jobWithKey(String tenantId, String idempotencyKey) {
        return Job.builder()
                .tenantId(tenantId)
                .jobType(JobType.UPDATE_CUSTOMERS)
                .payload(Map.of())
                .idempotencyKey(idempotencyKey)
                .build();
    }
}
