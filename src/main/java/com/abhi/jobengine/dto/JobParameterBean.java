package com.abhi.jobengine.dto;

import com.abhi.jobengine.domain.JobType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Type-safe job payload. Holds one parameter object per {@link JobType}; a valid payload has
 * exactly one of them set, and it must be the one matching the job's type (checked by the
 * service, see {@link #suppliedJobTypes()}).
 *
 * <p>Stored in {@code jobs.payload} (JSONB) as a JSON object containing only the populated
 * field, for example {@code {"generateReportParameters": {"reportType": "...", ...}}}.
 * Dates are written as ISO-8601 strings ({@code "2026-01-31"}).
 *
 * <p>To add a job type: add a nested parameter class, a field for it, and an entry in
 * {@link #SLOTS}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Job parameters. Set exactly one field: the one matching the job type.")
public class JobParameterBean implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Upper bound for the illustrative {@code batchSize} parameters. */
    public static final int MAX_BATCH_SIZE = 10_000;

    /** Payload field name and accessor for each job type. */
    private static final Map<JobType, Slot> SLOTS = new EnumMap<>(Map.of(
            JobType.GENERATE_REPORT,
            new Slot("generateReportParameters", JobParameterBean::getGenerateReportParameters),
            JobType.UPDATE_CUSTOMERS,
            new Slot("updateCustomersParameters", JobParameterBean::getUpdateCustomersParameters),
            JobType.RECONCILE_TRANSACTIONS,
            new Slot("reconcileTransactionsParameters", JobParameterBean::getReconcileTransactionsParameters),
            JobType.AGGREGATE_DAILY_METRICS,
            new Slot("aggregateDailyMetricsParameters", JobParameterBean::getAggregateDailyMetricsParameters)));

    /** Parameters for {@link JobType#GENERATE_REPORT}. */
    @Valid
    private GenerateReportParameters generateReportParameters;

    /** Parameters for {@link JobType#UPDATE_CUSTOMERS}. */
    @Valid
    private UpdateCustomersParameters updateCustomersParameters;

    /** Parameters for {@link JobType#RECONCILE_TRANSACTIONS}. */
    @Valid
    private ReconcileTransactionsParameters reconcileTransactionsParameters;

    /** Parameters for {@link JobType#AGGREGATE_DAILY_METRICS}. */
    @Valid
    private AggregateDailyMetricsParameters aggregateDailyMetricsParameters;

    /** Job types whose parameter object is set in this payload (empty if none is). */
    public Set<JobType> suppliedJobTypes() {
        Set<JobType> supplied = EnumSet.noneOf(JobType.class);
        SLOTS.forEach((jobType, slot) -> {
            if (slot.accessor().apply(this) != null) {
                supplied.add(jobType);
            }
        });
        return supplied;
    }

    /** The parameter object for the given job type, or {@code null} if it is not set. */
    public Object parametersFor(JobType jobType) {
        Slot slot = SLOTS.get(jobType);
        return slot == null ? null : slot.accessor().apply(this);
    }

    /**
     * JSON field name that carries the parameters of the given job type, for example
     * {@code generateReportParameters}, or {@code null} for a type with no parameter class.
     */
    public static String parameterFieldFor(JobType jobType) {
        Slot slot = SLOTS.get(jobType);
        return slot == null ? null : slot.fieldName();
    }

    /** Whether {@code start <= end}; open ranges are left to {@code @NotNull}. */
    private static boolean isOrderedRange(LocalDate start, LocalDate end) {
        return start == null || end == null || !end.isBefore(start);
    }

    private record Slot(String fieldName, Function<JobParameterBean, Object> accessor) {
    }

    /** Parameters for {@link JobType#GENERATE_REPORT}. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class GenerateReportParameters implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** Which report to build, for example {@code CUSTOMER_SUMMARY}. */
        @NotBlank
        @Size(max = 100)
        @Schema(example = "CUSTOMER_SUMMARY")
        private String reportType;

        /** First day covered by the report (inclusive). */
        @NotNull
        @Schema(example = "2026-01-01")
        private LocalDate startDate;

        /** Last day covered by the report (inclusive); not before {@code startDate}. */
        @NotNull
        @Schema(example = "2026-01-31")
        private LocalDate endDate;

        /** Output format, for example {@code CSV} or {@code PDF}. */
        @NotBlank
        @Size(max = 20)
        @Schema(example = "CSV")
        private String format;

        /** Who asked for the report. Informational only. */
        @Size(max = 100)
        @Schema(example = "admin")
        private String requestedBy;

        @JsonIgnore
        @AssertTrue(message = "endDate must not be before startDate")
        public boolean isDateRangeValid() {
            return isOrderedRange(startDate, endDate);
        }
    }

    /** Parameters for {@link JobType#UPDATE_CUSTOMERS}. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class UpdateCustomersParameters implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** Number of customers processed per batch. */
        @NotNull
        @Min(1)
        @Max(MAX_BATCH_SIZE)
        @Schema(example = "500")
        private Integer batchSize;

        /** Resume point: only customers with a greater id are processed. Optional. */
        @PositiveOrZero
        @Schema(example = "1000")
        private Long startAfterCustomerId;

        /** Kind of update to apply, for example {@code REFRESH_SEGMENTS}. */
        @NotBlank
        @Size(max = 100)
        @Schema(example = "REFRESH_SEGMENTS")
        private String updateType;
    }

    /** Parameters for {@link JobType#RECONCILE_TRANSACTIONS}. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class ReconcileTransactionsParameters implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** First transaction date to reconcile (inclusive). */
        @NotNull
        @Schema(example = "2026-01-01")
        private LocalDate startDate;

        /** Last transaction date to reconcile (inclusive); not before {@code startDate}. */
        @NotNull
        @Schema(example = "2026-01-07")
        private LocalDate endDate;

        /** Number of transactions processed per batch. */
        @NotNull
        @Min(1)
        @Max(MAX_BATCH_SIZE)
        @Schema(example = "1000")
        private Integer batchSize;

        /** How strictly to match, for example {@code STRICT} or {@code TOLERANT}. */
        @NotBlank
        @Size(max = 50)
        @Schema(example = "STRICT")
        private String reconciliationMode;

        @JsonIgnore
        @AssertTrue(message = "endDate must not be before startDate")
        public boolean isDateRangeValid() {
            return isOrderedRange(startDate, endDate);
        }
    }

    /** Parameters for {@link JobType#AGGREGATE_DAILY_METRICS}. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class AggregateDailyMetricsParameters implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** Day to aggregate. Must not be in the future. */
        @NotNull
        @PastOrPresent
        @Schema(example = "2026-01-31")
        private LocalDate businessDate;

        /** Metrics to compute, for example {@code ACTIVE_CUSTOMERS}. */
        @NotEmpty
        @Size(max = 50)
        @Schema(example = "[\"ACTIVE_CUSTOMERS\", \"TOTAL_REVENUE\"]")
        private List<@NotBlank @Size(max = 100) String> metricTypes;

        /** Number of rows processed per batch. Optional. */
        @Min(1)
        @Max(MAX_BATCH_SIZE)
        @Schema(example = "1000")
        private Integer batchSize;
    }
}
