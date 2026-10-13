package com.abhi.jobengine.controller;

import com.abhi.jobengine.domain.Job;
import com.abhi.jobengine.domain.JobStatus;
import com.abhi.jobengine.domain.JobType;
import com.abhi.jobengine.repository.JobRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API tests against the configured PostgreSQL (docker compose). Every test runs in a
 * transaction that is rolled back, and uses its own tenant id so existing rows do not interfere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JobControllerTests {

    private static final String JOBS = "/api/v1/jobs";

    private static final Map<JobType, String> VALID_PAYLOADS = Map.of(
            JobType.GENERATE_REPORT, """
                    {"generateReportParameters": {"reportType": "CUSTOMER_SUMMARY", "startDate": "2026-01-01",
                     "endDate": "2026-01-31", "format": "CSV", "requestedBy": "admin"}}""",
            JobType.UPDATE_CUSTOMERS, """
                    {"updateCustomersParameters": {"batchSize": 500, "startAfterCustomerId": 1000,
                     "updateType": "REFRESH_SEGMENTS"}}""",
            JobType.RECONCILE_TRANSACTIONS, """
                    {"reconcileTransactionsParameters": {"startDate": "2026-01-01", "endDate": "2026-01-07",
                     "batchSize": 1000, "reconciliationMode": "STRICT"}}""",
            JobType.AGGREGATE_DAILY_METRICS, """
                    {"aggregateDailyMetricsParameters": {"businessDate": "2026-01-31",
                     "metricTypes": ["ACTIVE_CUSTOMERS", "TOTAL_REVENUE"], "batchSize": 1000}}""");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String tenant;

    @BeforeEach
    void setUp() {
        tenant = "test-" + UUID.randomUUID();
    }

    @ParameterizedTest
    @EnumSource(JobType.class)
    void createsPendingJobOfEachType(JobType jobType) throws Exception {
        String parameterField = jsonMapper.readTree(VALID_PAYLOADS.get(jobType)).propertyNames().iterator().next();

        createJob(createBody(tenant, jobType, VALID_PAYLOADS.get(jobType)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString(JOBS + "/")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.tenantId").value(tenant))
                .andExpect(jsonPath("$.jobType").value(jobType.name()))
                .andExpect(jsonPath("$.jobStatus").value("PENDING"))
                .andExpect(jsonPath("$.priority").value(0))
                .andExpect(jsonPath("$.attemptCount").value(0))
                .andExpect(jsonPath("$.maxAttempts").value(3))
                .andExpect(jsonPath("$.nextRunAt").isNotEmpty())
                .andExpect(jsonPath("$.payload." + parameterField).isMap())
                .andExpect(jsonPath("$.lockedBy").doesNotExist())
                .andExpect(jsonPath("$.fencingToken").doesNotExist());
    }

    @Test
    void appliesSuppliedPriorityAndMaxAttempts() throws Exception {
        createJob("""
                {"tenantId": "%s", "jobType": "UPDATE_CUSTOMERS", "priority": 7, "maxAttempts": 5,
                 "payload": %s}""".formatted(tenant, VALID_PAYLOADS.get(JobType.UPDATE_CUSTOMERS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value(7))
                .andExpect(jsonPath("$.maxAttempts").value(5));
    }

    @Test
    void rejectsPayloadForAnotherJobType() throws Exception {
        createJob(createBody(tenant, JobType.GENERATE_REPORT, VALID_PAYLOADS.get(JobType.UPDATE_CUSTOMERS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("does not match job type GENERATE_REPORT")));
    }

    @Test
    void rejectsPayloadWithMultipleParameterObjects() throws Exception {
        String payload = """
                {"generateReportParameters": %s, "updateCustomersParameters": %s}""".formatted(
                jsonMapper.readTree(VALID_PAYLOADS.get(JobType.GENERATE_REPORT)).get("generateReportParameters"),
                jsonMapper.readTree(VALID_PAYLOADS.get(JobType.UPDATE_CUSTOMERS)).get("updateCustomersParameters"));

        createJob(createBody(tenant, JobType.GENERATE_REPORT, payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("must contain only")));
    }

    @Test
    void rejectsMissingOrEmptyPayload() throws Exception {
        createJob("""
                {"tenantId": "%s", "jobType": "GENERATE_REPORT"}""".formatted(tenant))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("payload")));

        createJob(createBody(tenant, JobType.GENERATE_REPORT, "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("must contain 'generateReportParameters'")));
    }

    @Test
    void rejectsInvalidNestedParameters() throws Exception {
        createJob(createBody(tenant, JobType.GENERATE_REPORT, """
                {"generateReportParameters": {"reportType": "", "startDate": "2026-02-01",
                 "endDate": "2026-01-01", "format": "CSV"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("payload.generateReportParameters.reportType")))
                .andExpect(jsonPath("$.fieldErrors[*].message", hasItem("endDate must not be before startDate")));

        createJob(createBody(tenant, JobType.UPDATE_CUSTOMERS, """
                {"updateCustomersParameters": {"batchSize": 0, "updateType": "REFRESH_SEGMENTS"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("payload.updateCustomersParameters.batchSize")));

        createJob(createBody(tenant, JobType.AGGREGATE_DAILY_METRICS, """
                {"aggregateDailyMetricsParameters": {"businessDate": "2999-01-01", "metricTypes": []}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem(
                        "payload.aggregateDailyMetricsParameters.businessDate")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem(
                        "payload.aggregateDailyMetricsParameters.metricTypes")));
    }

    @Test
    void rejectsUnknownJobTypeAndMalformedDates() throws Exception {
        createJob(createBody(tenant, null, "{}").replace("\"jobType\": null", "\"jobType\": \"SEND_EMAILS\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("allowed values: [GENERATE_REPORT")));

        createJob(createBody(tenant, JobType.GENERATE_REPORT, """
                {"generateReportParameters": {"reportType": "X", "startDate": "2026-13-01",
                 "endDate": "2026-01-31", "format": "CSV"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("payload.generateReportParameters.startDate")));

        createJob("{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
    }

    @Test
    void rejectsDuplicateIdempotencyKeyWithinTenantOnly() throws Exception {
        String body = """
                {"tenantId": "%s", "jobType": "UPDATE_CUSTOMERS", "idempotencyKey": "key-1", "payload": %s}""";
        String payload = VALID_PAYLOADS.get(JobType.UPDATE_CUSTOMERS);

        createJob(body.formatted(tenant, payload)).andExpect(status().isCreated());
        createJob(body.formatted(tenant + "-other", payload)).andExpect(status().isCreated());
        // Last: the failed insert aborts the surrounding (test) transaction.
        createJob(body.formatted(tenant, payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("idempotency key 'key-1'")));
    }

    // --- JSONB storage --------------------------------------------------------------------

    @Test
    void storesPayloadAsJsonbObjectAndReadsItBack() throws Exception {
        String id = createJobId(JobType.GENERATE_REPORT);

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT pg_typeof(payload)::text AS column_type,
                       jsonb_typeof(payload) AS json_type,
                       (SELECT count(*) FROM jsonb_object_keys(payload)) AS key_count,
                       payload #>> '{generateReportParameters,startDate}' AS start_date
                FROM jobs WHERE id = ?::uuid""", id);
        assertThat(row)
                .containsEntry("column_type", "jsonb")
                .containsEntry("json_type", "object")
                .containsEntry("key_count", 1L)
                .containsEntry("start_date", "2026-01-01");

        // Read from the database, not the persistence context.
        entityManager.clear();
        mockMvc.perform(get(JOBS + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.generateReportParameters.startDate").value("2026-01-01"))
                .andExpect(jsonPath("$.payload.generateReportParameters.endDate").value("2026-01-31"))
                .andExpect(jsonPath("$.payload.updateCustomersParameters").doesNotExist());
    }

    // --- get ------------------------------------------------------------------------------

    @Test
    void getsExistingJobAndReturns404OrBadRequestOtherwise() throws Exception {
        String id = createJobId(JobType.RECONCILE_TRANSACTIONS);

        mockMvc.perform(get(JOBS + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.payload.reconcileTransactionsParameters.reconciliationMode").value("STRICT"));
        mockMvc.perform(get(JOBS + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get(JOBS + "/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("parameter 'id'")));
    }

    // --- update ---------------------------------------------------------------------------

    @Test
    void updatesPendingJob() throws Exception {
        String id = createJobId(JobType.GENERATE_REPORT);

        updateJob(id, """
                {"priority": 9, "maxAttempts": 5, "payload": {"generateReportParameters": {
                 "reportType": "CUSTOMER_SUMMARY", "startDate": "2026-02-01", "endDate": "2026-02-28",
                 "format": "PDF"}}}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value(9))
                .andExpect(jsonPath("$.maxAttempts").value(5))
                .andExpect(jsonPath("$.jobStatus").value("PENDING"))
                .andExpect(jsonPath("$.jobType").value("GENERATE_REPORT"))
                .andExpect(jsonPath("$.payload.generateReportParameters.format").value("PDF"));

        entityManager.clear();
        Job stored = jobRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(stored.getPriority()).isEqualTo(9);
        assertThat(stored.getUpdatedAt()).isAfterOrEqualTo(stored.getCreatedAt());
    }

    @Test
    void keepsPriorityWhenOmittedAndRejectsMismatchedPayload() throws Exception {
        String id = createJobId(JobType.GENERATE_REPORT);

        updateJob(id, "{\"payload\": %s}".formatted(VALID_PAYLOADS.get(JobType.GENERATE_REPORT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value(0));
        updateJob(id, "{\"payload\": %s}".formatted(VALID_PAYLOADS.get(JobType.UPDATE_CUSTOMERS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("does not match job type GENERATE_REPORT")));
    }

    @Test
    void rejectsUpdateOfRunningOrFinishedJob() throws Exception {
        String body = "{\"payload\": %s}".formatted(VALID_PAYLOADS.get(JobType.GENERATE_REPORT));

        updateJob(saveJobWithStatus(JobStatus.RUNNING), body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("is RUNNING")));
        updateJob(saveJobWithStatus(JobStatus.SUCCEEDED), body)
                .andExpect(status().isConflict());
    }

    @Test
    void returns404WhenUpdatingMissingJob() throws Exception {
        updateJob(UUID.randomUUID().toString(), "{\"payload\": %s}".formatted(VALID_PAYLOADS.get(JobType.GENERATE_REPORT)))
                .andExpect(status().isNotFound());
    }

    // --- delete ---------------------------------------------------------------------------

    @Test
    void deletesPendingJob() throws Exception {
        String id = createJobId(JobType.UPDATE_CUSTOMERS);

        mockMvc.perform(delete(JOBS + "/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get(JOBS + "/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(delete(JOBS + "/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsDeletionOfRunningJob() throws Exception {
        String id = saveJobWithStatus(JobStatus.RUNNING);

        mockMvc.perform(delete(JOBS + "/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("RUNNING and cannot be deleted")));
        assertThat(jobRepository.findById(UUID.fromString(id))).isPresent();
    }

    // --- list -----------------------------------------------------------------------------

    @Test
    void pagesAndSortsJobs() throws Exception {
        for (int priority : new int[]{3, 1, 2}) {
            createJob("""
                    {"tenantId": "%s", "jobType": "GENERATE_REPORT", "priority": %d, "payload": %s}"""
                    .formatted(tenant, priority, VALID_PAYLOADS.get(JobType.GENERATE_REPORT)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get(JOBS).param("tenantId", tenant).param("sortBy", "priority")
                        .param("direction", "asc").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].priority", contains(1, 2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        mockMvc.perform(get(JOBS).param("tenantId", tenant).param("sortBy", "priority")
                        .param("direction", "ASC").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].priority", contains(3)))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(true));

        mockMvc.perform(get(JOBS).param("tenantId", tenant).param("sortBy", "priority"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].priority", contains(3, 2, 1)));
    }

    @Test
    void filtersByTenantStatusAndType() throws Exception {
        createJobId(JobType.GENERATE_REPORT);
        createJobId(JobType.UPDATE_CUSTOMERS);
        saveJobWithStatus(JobStatus.RUNNING);

        mockMvc.perform(get(JOBS).param("tenantId", tenant))
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get(JOBS).param("tenantId", tenant).param("jobType", "UPDATE_CUSTOMERS"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].jobType").value("UPDATE_CUSTOMERS"));
        mockMvc.perform(get(JOBS).param("tenantId", tenant).param("jobStatus", "RUNNING"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].jobStatus").value("RUNNING"));
        mockMvc.perform(get(JOBS).param("tenantId", tenant).param("jobStatus", "PENDING")
                        .param("jobType", "GENERATE_REPORT"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(JOBS).param("tenantId", tenant + "-nobody"))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void rejectsInvalidPaginationSortAndFilterParameters() throws Exception {
        mockMvc.perform(get(JOBS).param("sortBy", "lockedBy"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Unsupported sortBy 'lockedBy'")));
        mockMvc.perform(get(JOBS).param("direction", "SIDEWAYS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Unsupported direction")));
        mockMvc.perform(get(JOBS).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));
        mockMvc.perform(get(JOBS).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"));
        mockMvc.perform(get(JOBS).param("jobStatus", "DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("allowed values: [PENDING")));
    }

    // --- helpers --------------------------------------------------------------------------

    private ResultActions createJob(String body) throws Exception {
        return mockMvc.perform(post(JOBS).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions updateJob(String id, String body) throws Exception {
        return mockMvc.perform(put(JOBS + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String createJobId(JobType jobType) throws Exception {
        String response = createJob(createBody(tenant, jobType, VALID_PAYLOADS.get(jobType)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(response).get("id").asString();
    }

    /** Inserts a job directly, bypassing the API, to simulate a status set by a worker. */
    @SuppressWarnings("unchecked")
    private String saveJobWithStatus(JobStatus jobStatus) {
        Map<String, Object> payload = jsonMapper.readValue(VALID_PAYLOADS.get(JobType.GENERATE_REPORT), Map.class);
        Job job = jobRepository.saveAndFlush(Job.builder()
                .tenantId(tenant)
                .jobType(JobType.GENERATE_REPORT)
                .jobStatus(jobStatus)
                .attemptCount(1)
                .payload(payload)
                .build());
        return job.getId().toString();
    }

    private static String createBody(String tenantId, JobType jobType, String payload) {
        String type = jobType == null ? "null" : "\"" + jobType.name() + "\"";
        return """
                {"tenantId": "%s", "jobType": %s, "payload": %s}""".formatted(tenantId, type, payload);
    }
}
