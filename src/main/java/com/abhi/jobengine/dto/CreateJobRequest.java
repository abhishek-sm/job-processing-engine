package com.abhi.jobengine.dto;

import com.abhi.jobengine.domain.JobType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Body of {@code POST /api/v1/jobs}. Status, attempt count, timestamps and worker metadata
 * are set by the server and cannot be supplied.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Schema(description = "Request to submit a new job")
public class CreateJobRequest {

    public static final int MIN_PRIORITY = 0;
    public static final int MAX_PRIORITY = 100;
    public static final int MAX_MAX_ATTEMPTS = 10;

    /** Tenant that owns the job. Not an authentication boundary yet. */
    @NotBlank
    @Size(max = 100)
    @Schema(description = "Tenant that owns the job", example = "tenant-a",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String tenantId;

    /** Selects the executor; immutable after creation. */
    @NotNull
    @Schema(description = "Kind of job; immutable after creation", example = "GENERATE_REPORT",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private JobType jobType;

    /** Parameters; must contain exactly the parameter object matching {@link #jobType}. */
    @NotNull
    @Valid
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private JobParameterBean payload;

    /** Higher values are dispatched first. Defaults to 0. */
    @Min(MIN_PRIORITY)
    @Max(MAX_PRIORITY)
    @Schema(description = "Higher values are dispatched first (0-100). Defaults to 0.", example = "5")
    private Integer priority;

    /** Maximum execution attempts. Defaults to {@code job-engine.default-max-attempts}. */
    @Min(1)
    @Max(MAX_MAX_ATTEMPTS)
    @Schema(description = "Maximum execution attempts (1-10). Defaults to the engine setting (3).",
            example = "3")
    private Integer maxAttempts;

    /** Optional key that makes the submission idempotent per tenant. */
    @Size(max = 255)
    @Pattern(regexp = ".*\\S.*", message = "must not be blank when provided")
    @Schema(description = "Optional key, unique per tenant. Re-submitting the same key returns 409.",
            example = "report-2026-01")
    private String idempotencyKey;
}
