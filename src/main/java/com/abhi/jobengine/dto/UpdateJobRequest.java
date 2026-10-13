package com.abhi.jobengine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Body of {@code PUT /api/v1/jobs/{id}}. Only these client-editable fields can change; the
 * tenant, job type, status and worker metadata are fixed. {@code payload} is replaced as a
 * whole; {@code priority} and {@code maxAttempts} keep their current value when omitted.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Schema(description = "Editable fields of a PENDING or RETRY_WAIT job")
public class UpdateJobRequest {

    /** New parameters; must match the job's existing type. */
    @NotNull
    @Valid
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private JobParameterBean payload;

    /** New priority; unchanged when omitted. */
    @Min(CreateJobRequest.MIN_PRIORITY)
    @Max(CreateJobRequest.MAX_PRIORITY)
    @Schema(description = "New priority (0-100); unchanged when omitted", example = "10")
    private Integer priority;

    /** New attempt limit; unchanged when omitted. Must not be below the attempts already made. */
    @Min(1)
    @Max(CreateJobRequest.MAX_MAX_ATTEMPTS)
    @Schema(description = "New attempt limit (1-10, and not below attemptCount); unchanged when omitted",
            example = "5")
    private Integer maxAttempts;
}
