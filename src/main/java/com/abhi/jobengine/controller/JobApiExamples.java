package com.abhi.jobengine.controller;

/** JSON examples shown in Swagger UI for {@link JobController}. Documentation only. */
final class JobApiExamples {

    private JobApiExamples() {
    }

    static final String CREATE_GENERATE_REPORT = """
            {
              "tenantId": "tenant-a",
              "jobType": "GENERATE_REPORT",
              "priority": 5,
              "maxAttempts": 3,
              "idempotencyKey": "report-2026-01",
              "payload": {
                "generateReportParameters": {
                  "reportType": "CUSTOMER_SUMMARY",
                  "startDate": "2026-01-01",
                  "endDate": "2026-01-31",
                  "format": "CSV",
                  "requestedBy": "admin"
                }
              }
            }""";

    static final String CREATE_UPDATE_CUSTOMERS = """
            {
              "tenantId": "tenant-a",
              "jobType": "UPDATE_CUSTOMERS",
              "payload": {
                "updateCustomersParameters": {
                  "batchSize": 500,
                  "startAfterCustomerId": 1000,
                  "updateType": "REFRESH_SEGMENTS"
                }
              }
            }""";

    static final String CREATE_RECONCILE_TRANSACTIONS = """
            {
              "tenantId": "tenant-b",
              "jobType": "RECONCILE_TRANSACTIONS",
              "priority": 10,
              "payload": {
                "reconcileTransactionsParameters": {
                  "startDate": "2026-01-01",
                  "endDate": "2026-01-07",
                  "batchSize": 1000,
                  "reconciliationMode": "STRICT"
                }
              }
            }""";

    static final String CREATE_AGGREGATE_DAILY_METRICS = """
            {
              "tenantId": "tenant-b",
              "jobType": "AGGREGATE_DAILY_METRICS",
              "payload": {
                "aggregateDailyMetricsParameters": {
                  "businessDate": "2026-01-31",
                  "metricTypes": ["ACTIVE_CUSTOMERS", "TOTAL_REVENUE"],
                  "batchSize": 1000
                }
              }
            }""";

    static final String UPDATE_GENERATE_REPORT = """
            {
              "priority": 10,
              "maxAttempts": 5,
              "payload": {
                "generateReportParameters": {
                  "reportType": "CUSTOMER_SUMMARY",
                  "startDate": "2026-02-01",
                  "endDate": "2026-02-28",
                  "format": "PDF",
                  "requestedBy": "admin"
                }
              }
            }""";

    static final String JOB_RESPONSE = """
            {
              "id": "3f1c2a9e-6b1d-4c8e-9a52-1f0e7d4b2c11",
              "tenantId": "tenant-a",
              "jobType": "GENERATE_REPORT",
              "jobStatus": "PENDING",
              "payload": {
                "generateReportParameters": {
                  "reportType": "CUSTOMER_SUMMARY",
                  "startDate": "2026-01-01",
                  "endDate": "2026-01-31",
                  "format": "CSV",
                  "requestedBy": "admin"
                }
              },
              "priority": 5,
              "attemptCount": 0,
              "maxAttempts": 3,
              "idempotencyKey": "report-2026-01",
              "nextRunAt": "2026-10-13T09:15:30.123456Z",
              "createdAt": "2026-10-13T09:15:30.123456Z",
              "updatedAt": "2026-10-13T09:15:30.123456Z"
            }""";

    static final String PAGE_RESPONSE = """
            {
              "content": [
                {
                  "id": "3f1c2a9e-6b1d-4c8e-9a52-1f0e7d4b2c11",
                  "tenantId": "tenant-a",
                  "jobType": "UPDATE_CUSTOMERS",
                  "jobStatus": "PENDING",
                  "payload": {
                    "updateCustomersParameters": {
                      "batchSize": 500,
                      "startAfterCustomerId": 1000,
                      "updateType": "REFRESH_SEGMENTS"
                    }
                  },
                  "priority": 0,
                  "attemptCount": 0,
                  "maxAttempts": 3,
                  "nextRunAt": "2026-10-13T09:15:30.123456Z",
                  "createdAt": "2026-10-13T09:15:30.123456Z",
                  "updatedAt": "2026-10-13T09:15:30.123456Z"
                }
              ],
              "page": 0,
              "size": 20,
              "totalElements": 1,
              "totalPages": 1,
              "hasNext": false,
              "hasPrevious": false
            }""";

    static final String VALIDATION_ERROR = """
            {
              "timestamp": "2026-10-13T09:15:30.123Z",
              "status": 400,
              "error": "Bad Request",
              "message": "Request validation failed",
              "path": "/api/v1/jobs",
              "fieldErrors": [
                {"field": "tenantId", "message": "must not be blank"},
                {"field": "payload.generateReportParameters.dateRangeValid",
                 "message": "endDate must not be before startDate"}
              ]
            }""";

    static final String PAYLOAD_MISMATCH_ERROR = """
            {
              "timestamp": "2026-10-13T09:15:30.123Z",
              "status": 400,
              "error": "Bad Request",
              "message": "payload contains 'updateCustomersParameters', which does not match job type GENERATE_REPORT; expected 'generateReportParameters'",
              "path": "/api/v1/jobs"
            }""";

    static final String DUPLICATE_KEY_ERROR = """
            {
              "timestamp": "2026-10-13T09:15:30.123Z",
              "status": 409,
              "error": "Conflict",
              "message": "A job with idempotency key 'report-2026-01' already exists for tenant 'tenant-a'",
              "path": "/api/v1/jobs"
            }""";

    static final String RUNNING_CONFLICT_ERROR = """
            {
              "timestamp": "2026-10-13T09:15:30.123Z",
              "status": 409,
              "error": "Conflict",
              "message": "Job 3f1c2a9e-6b1d-4c8e-9a52-1f0e7d4b2c11 is RUNNING and cannot be deleted",
              "path": "/api/v1/jobs/3f1c2a9e-6b1d-4c8e-9a52-1f0e7d4b2c11"
            }""";

    static final String NOT_FOUND_ERROR = """
            {
              "timestamp": "2026-10-13T09:15:30.123Z",
              "status": 404,
              "error": "Not Found",
              "message": "Job 3f1c2a9e-6b1d-4c8e-9a52-1f0e7d4b2c11 not found",
              "path": "/api/v1/jobs/3f1c2a9e-6b1d-4c8e-9a52-1f0e7d4b2c11"
            }""";

    static final String INVALID_SORT_ERROR = """
            {
              "timestamp": "2026-10-13T09:15:30.123Z",
              "status": 400,
              "error": "Bad Request",
              "message": "Unsupported sortBy 'lockedBy'; allowed values: [createdAt, updatedAt, priority, nextRunAt, jobStatus, jobType, tenantId]",
              "path": "/api/v1/jobs"
            }""";
}
