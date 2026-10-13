package com.abhi.jobengine.controller;

import com.abhi.jobengine.domain.JobStatus;
import com.abhi.jobengine.domain.JobType;
import com.abhi.jobengine.dto.ApiError;
import com.abhi.jobengine.dto.CreateJobRequest;
import com.abhi.jobengine.dto.JobResponse;
import com.abhi.jobengine.dto.PageResponse;
import com.abhi.jobengine.dto.UpdateJobRequest;
import com.abhi.jobengine.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Job management API. All business rules live in {@link JobService}.
 *
 * <p>{@code tenantId} is a plain filter/field here, not an authenticated tenant boundary.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs", description = "Submit, inspect, edit and delete jobs")
public class JobController {

    private static final String JSON = "application/json";

    private final JobService jobService;

    @PostMapping
    @Operation(summary = "Submit a job",
            description = "Persists a new PENDING job and returns it. The payload must contain exactly the "
                    + "parameter object matching jobType. Status, attempts and timestamps are set by the server.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = CreateJobRequest.class),
                    examples = {
                            @ExampleObject(name = "GENERATE_REPORT", value = JobApiExamples.CREATE_GENERATE_REPORT),
                            @ExampleObject(name = "UPDATE_CUSTOMERS", value = JobApiExamples.CREATE_UPDATE_CUSTOMERS),
                            @ExampleObject(name = "RECONCILE_TRANSACTIONS",
                                    value = JobApiExamples.CREATE_RECONCILE_TRANSACTIONS),
                            @ExampleObject(name = "AGGREGATE_DAILY_METRICS",
                                    value = JobApiExamples.CREATE_AGGREGATE_DAILY_METRICS)})))
    @ApiResponse(responseCode = "201", description = "Job created; Location header points to it",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = JobResponse.class),
                    examples = @ExampleObject(value = JobApiExamples.JOB_RESPONSE)))
    @ApiResponse(responseCode = "400", description = "Invalid request or payload does not match jobType",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiError.class), examples = {
                    @ExampleObject(name = "Validation error", value = JobApiExamples.VALIDATION_ERROR),
                    @ExampleObject(name = "Payload mismatch", value = JobApiExamples.PAYLOAD_MISMATCH_ERROR)}))
    @ApiResponse(responseCode = "409", description = "Idempotency key already used by this tenant",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = JobApiExamples.DUPLICATE_KEY_ERROR)))
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody CreateJobRequest request) {
        JobResponse created = jobService.createJob(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a job", description = "Returns one job by id.")
    @ApiResponse(responseCode = "200", description = "Job found",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = JobResponse.class),
                    examples = @ExampleObject(value = JobApiExamples.JOB_RESPONSE)))
    @ApiResponse(responseCode = "400", description = "id is not a valid UUID",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No job with this id",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = JobApiExamples.NOT_FOUND_ERROR)))
    public JobResponse getJob(@Parameter(description = "Job id") @PathVariable UUID id) {
        return jobService.getJob(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a job",
            description = "Replaces the payload and optionally the priority and maxAttempts of a PENDING or "
                    + "RETRY_WAIT job. The job type, tenant and status cannot be changed; the payload must "
                    + "match the existing job type.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = UpdateJobRequest.class),
                    examples = @ExampleObject(name = "GENERATE_REPORT", value = JobApiExamples.UPDATE_GENERATE_REPORT))))
    @ApiResponse(responseCode = "200", description = "Job updated",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = JobResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request or payload does not match the job type",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = JobApiExamples.PAYLOAD_MISMATCH_ERROR)))
    @ApiResponse(responseCode = "404", description = "No job with this id",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Job is running or finished and cannot be edited",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiError.class)))
    public JobResponse updateJob(@Parameter(description = "Job id") @PathVariable UUID id,
                                 @Valid @RequestBody UpdateJobRequest request) {
        return jobService.updateJob(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a job",
            description = "Permanently deletes a job that is not RUNNING. Pending, waiting and finished jobs "
                    + "can be deleted.")
    @ApiResponse(responseCode = "204", description = "Job deleted")
    @ApiResponse(responseCode = "404", description = "No job with this id",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Job is RUNNING",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = JobApiExamples.RUNNING_CONFLICT_ERROR)))
    public void deleteJob(@Parameter(description = "Job id") @PathVariable UUID id) {
        jobService.deleteJob(id);
    }

    @GetMapping
    @Operation(summary = "List jobs",
            description = "Returns a page of jobs, optionally filtered by tenant, status and type.")
    @ApiResponse(responseCode = "200", description = "A page of jobs",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = PageResponse.class),
                    examples = @ExampleObject(value = JobApiExamples.PAGE_RESPONSE)))
    @ApiResponse(responseCode = "400", description = "Invalid pagination, sort or filter parameter",
            content = @Content(mediaType = JSON, schema = @Schema(implementation = ApiError.class),
                    examples = @ExampleObject(value = JobApiExamples.INVALID_SORT_ERROR)))
    public PageResponse<JobResponse> listJobs(
            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size (1-100)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "Field to sort by", schema = @Schema(defaultValue = "createdAt",
                    allowableValues = {"createdAt", "updatedAt", "priority", "nextRunAt", "jobStatus",
                            "jobType", "tenantId"}))
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction", schema = @Schema(defaultValue = "DESC",
                    allowableValues = {"ASC", "DESC"}))
            @RequestParam(defaultValue = "DESC") String direction,
            @Parameter(description = "Only jobs of this tenant")
            @RequestParam(required = false) String tenantId,
            @Parameter(description = "Only jobs in this status")
            @RequestParam(required = false) JobStatus jobStatus,
            @Parameter(description = "Only jobs of this type")
            @RequestParam(required = false) JobType jobType) {
        return jobService.listJobs(tenantId, jobStatus, jobType, page, size, sortBy, direction);
    }
}
