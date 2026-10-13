package com.abhi.jobengine.dto;

import com.abhi.jobengine.domain.JobStatus;
import com.abhi.jobengine.domain.JobType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * API view of a job. Worker-coordination fields (lock owner, lease, heartbeat, fencing token)
 * are deliberately left out.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Schema(description = "A job as returned by the API")
public class JobResponse {

    @Schema(example = "3f1c2a9e-6b1d-4c8e-9a52-1f0e7d4b2c11")
    private UUID id;

    @Schema(example = "tenant-a")
    private String tenantId;

    @Schema(example = "GENERATE_REPORT")
    private JobType jobType;

    @Schema(example = "PENDING")
    private JobStatus jobStatus;

    private JobParameterBean payload;

    @Schema(example = "5")
    private Integer priority;

    @Schema(example = "0")
    private Integer attemptCount;

    @Schema(example = "3")
    private Integer maxAttempts;

    @Schema(example = "report-2026-01")
    private String idempotencyKey;

    @Schema(description = "Earliest time the job may run")
    private Instant nextRunAt;

    @Schema(description = "Details of the most recent failure, if any")
    private String errorMessage;

    private Instant startedAt;

    private Instant completedAt;

    private Instant createdAt;

    private Instant updatedAt;
}
