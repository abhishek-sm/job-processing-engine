package com.abhi.jobengine.service;

import com.abhi.jobengine.domain.Job;
import com.abhi.jobengine.dto.JobParameterBean;
import com.abhi.jobengine.dto.JobResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Converts between the {@link Job} entity and API DTOs.
 *
 * <p>The entity keeps its payload as a {@code Map<String, Object>} that Hibernate writes to the
 * {@code JSONB} column. This class converts {@link JobParameterBean} to and from that map with
 * the application's {@link JsonMapper}, the same mapper used for HTTP bodies, so the stored JSON
 * has the same shape as the API (only the populated parameter object, ISO-8601 dates).
 */
@Component
@RequiredArgsConstructor
public class JobMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JsonMapper jsonMapper;

    /** Payload to the JSON-object map stored in {@code jobs.payload}. */
    public Map<String, Object> toPayloadMap(JobParameterBean payload) {
        return jsonMapper.convertValue(payload, MAP_TYPE);
    }

    /** Stored {@code jobs.payload} map back to the typed payload. */
    public JobParameterBean toParameterBean(Job job) {
        try {
            return jsonMapper.convertValue(job.getPayload(), JobParameterBean.class);
        } catch (JacksonException | IllegalArgumentException ex) {
            throw new IllegalStateException("Stored payload of job " + job.getId() + " cannot be read", ex);
        }
    }

    public JobResponse toResponse(Job job) {
        return JobResponse.builder()
                .id(job.getId())
                .tenantId(job.getTenantId())
                .jobType(job.getJobType())
                .jobStatus(job.getJobStatus())
                .payload(toParameterBean(job))
                .priority(job.getPriority())
                .attemptCount(job.getAttemptCount())
                .maxAttempts(job.getMaxAttempts())
                .idempotencyKey(job.getIdempotencyKey())
                .nextRunAt(job.getNextRunAt())
                .errorMessage(job.getErrorMessage())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
