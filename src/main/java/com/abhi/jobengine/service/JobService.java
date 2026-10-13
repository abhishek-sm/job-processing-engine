package com.abhi.jobengine.service;

import com.abhi.jobengine.domain.Job;
import com.abhi.jobengine.domain.JobStatus;
import com.abhi.jobengine.domain.JobType;
import com.abhi.jobengine.dto.CreateJobRequest;
import com.abhi.jobengine.dto.JobParameterBean;
import com.abhi.jobengine.dto.JobResponse;
import com.abhi.jobengine.dto.PageResponse;
import com.abhi.jobengine.dto.UpdateJobRequest;
import com.abhi.jobengine.exception.DuplicateIdempotencyKeyException;
import com.abhi.jobengine.exception.InvalidJobRequestException;
import com.abhi.jobengine.exception.JobNotFoundException;
import com.abhi.jobengine.exception.JobStateConflictException;
import com.abhi.jobengine.repository.JobRepository;
import com.abhi.jobengine.repository.JobSpecifications;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Job management rules: payload/type validation, creation defaults, and which lifecycle states
 * allow edits and deletes. Does not execute or dispatch jobs.
 *
 * <p>Lifecycle rules:
 * <ul>
 *   <li>The job type and tenant are fixed at creation.</li>
 *   <li>Only jobs that are waiting to run ({@code PENDING}, {@code RETRY_WAIT}) can be updated.</li>
 *   <li>Any job except a {@code RUNNING} one can be deleted (physically removed).</li>
 * </ul>
 * Update and delete lock the row ({@code SELECT ... FOR UPDATE}) before checking the status, so a
 * worker cannot claim the job between the check and the write.
 */
@Service
@Transactional(readOnly = true)
public class JobService {

    /** Fields clients may sort by. Anything else is rejected rather than passed to the query. */
    public static final List<String> SORTABLE_FIELDS = List.of(
            "createdAt", "updatedAt", "priority", "nextRunAt", "jobStatus", "jobType", "tenantId");

    private static final Set<JobStatus> EDITABLE_STATUSES = EnumSet.of(JobStatus.PENDING, JobStatus.RETRY_WAIT);

    /** Name of the partial unique index on (tenant_id, idempotency_key) from V1. */
    private static final String IDEMPOTENCY_INDEX = "ux_jobs_tenant_idempotency_key";

    private final JobRepository jobRepository;
    private final JobMapper jobMapper;
    private final int defaultMaxAttempts;

    public JobService(JobRepository jobRepository, JobMapper jobMapper,
                      @Value("${job-engine.default-max-attempts:" + Job.DEFAULT_MAX_ATTEMPTS + "}")
                      int defaultMaxAttempts) {
        this.jobRepository = jobRepository;
        this.jobMapper = jobMapper;
        this.defaultMaxAttempts = defaultMaxAttempts;
    }

    /**
     * Persists a new {@code PENDING} job that is runnable immediately. A duplicate idempotency
     * key for the same tenant is detected by the unique index, which is also safe when two
     * identical requests race.
     */
    @Transactional
    public JobResponse createJob(CreateJobRequest request) {
        validatePayload(request.getJobType(), request.getPayload());

        Job job = Job.builder()
                .tenantId(request.getTenantId())
                .jobType(request.getJobType())
                .jobStatus(JobStatus.PENDING)
                .payload(jobMapper.toPayloadMap(request.getPayload()))
                .priority(request.getPriority() != null ? request.getPriority() : Job.DEFAULT_PRIORITY)
                .maxAttempts(request.getMaxAttempts() != null ? request.getMaxAttempts() : defaultMaxAttempts)
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        try {
            // Flush now so a unique-index violation surfaces here, not at commit.
            return jobMapper.toResponse(jobRepository.saveAndFlush(job));
        } catch (DataIntegrityViolationException ex) {
            if (isIdempotencyKeyViolation(ex)) {
                throw new DuplicateIdempotencyKeyException(request.getTenantId(), request.getIdempotencyKey(), ex);
            }
            throw ex;
        }
    }

    public JobResponse getJob(UUID id) {
        return jobRepository.findById(id)
                .map(jobMapper::toResponse)
                .orElseThrow(() -> new JobNotFoundException(id));
    }

    /**
     * Replaces the payload and optionally the priority and attempt limit of a job that has not
     * started running. The job type stays the same, so the new payload must match it.
     */
    @Transactional
    public JobResponse updateJob(UUID id, UpdateJobRequest request) {
        Job job = jobRepository.findByIdForUpdate(id).orElseThrow(() -> new JobNotFoundException(id));
        if (!EDITABLE_STATUSES.contains(job.getJobStatus())) {
            throw new JobStateConflictException("Job " + id + " is " + job.getJobStatus()
                    + "; only jobs in " + EDITABLE_STATUSES + " can be updated");
        }
        validatePayload(job.getJobType(), request.getPayload());
        if (request.getMaxAttempts() != null && request.getMaxAttempts() < job.getAttemptCount()) {
            throw new InvalidJobRequestException("maxAttempts (" + request.getMaxAttempts()
                    + ") must not be less than the attempts already made (" + job.getAttemptCount() + ")");
        }

        job.setPayload(jobMapper.toPayloadMap(request.getPayload()));
        if (request.getPriority() != null) {
            job.setPriority(request.getPriority());
        }
        if (request.getMaxAttempts() != null) {
            job.setMaxAttempts(request.getMaxAttempts());
        }
        // Flush so @PreUpdate sets updatedAt before the response is built.
        return jobMapper.toResponse(jobRepository.saveAndFlush(job));
    }

    /**
     * Physically deletes a job unless it is {@code RUNNING}. Pending jobs disappear before they
     * are dispatched; finished jobs are removed from history.
     */
    @Transactional
    public void deleteJob(UUID id) {
        Job job = jobRepository.findByIdForUpdate(id).orElseThrow(() -> new JobNotFoundException(id));
        if (job.getJobStatus() == JobStatus.RUNNING) {
            throw new JobStateConflictException("Job " + id + " is RUNNING and cannot be deleted");
        }
        jobRepository.delete(job);
    }

    /**
     * Lists jobs matching the optional filters. {@code sortBy} must be one of
     * {@link #SORTABLE_FIELDS}; ties are broken by id so pages are stable.
     */
    public PageResponse<JobResponse> listJobs(String tenantId, JobStatus jobStatus, JobType jobType,
                                              int page, int size, String sortBy, String direction) {
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new InvalidJobRequestException("Unsupported sortBy '" + sortBy + "'; allowed values: "
                    + SORTABLE_FIELDS);
        }
        Sort.Direction sortDirection = Sort.Direction.fromOptionalString(direction)
                .orElseThrow(() -> new InvalidJobRequestException("Unsupported direction '" + direction
                        + "'; allowed values: [ASC, DESC]"));

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(sortDirection, sortBy).and(Sort.by(Sort.Direction.ASC, "id")));
        return PageResponse.from(jobRepository
                .findAll(JobSpecifications.matching(tenantId, jobStatus, jobType), pageable)
                .map(jobMapper::toResponse));
    }

    /**
     * Checks that the payload carries exactly one parameter object and that it belongs to the
     * job type. Relies on {@link JobParameterBean#suppliedJobTypes()}, so a new job type needs no
     * change here.
     */
    private static void validatePayload(JobType jobType, JobParameterBean payload) {
        String expectedField = JobParameterBean.parameterFieldFor(jobType);
        if (expectedField == null) {
            throw new InvalidJobRequestException("Job type " + jobType + " has no payload definition");
        }
        if (payload == null) {
            throw new InvalidJobRequestException("payload is required");
        }
        Set<JobType> supplied = payload.suppliedJobTypes();
        if (supplied.isEmpty()) {
            throw new InvalidJobRequestException("payload must contain '" + expectedField
                    + "' for job type " + jobType);
        }
        if (supplied.size() > 1) {
            throw new InvalidJobRequestException("payload must contain only '" + expectedField
                    + "' for job type " + jobType + ", but it contains parameters for " + supplied);
        }
        if (!supplied.contains(jobType)) {
            JobType suppliedType = supplied.iterator().next();
            throw new InvalidJobRequestException("payload contains '"
                    + JobParameterBean.parameterFieldFor(suppliedType) + "', which does not match job type "
                    + jobType + "; expected '" + expectedField + "'");
        }
    }

    private static boolean isIdempotencyKeyViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(IDEMPOTENCY_INDEX);
    }
}
