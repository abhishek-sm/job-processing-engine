package com.abhi.jobengine.dto;

import com.abhi.jobengine.domain.JobType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JobParameterBeanTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void everyJobTypeHasAPayloadField() {
        for (JobType jobType : JobType.values()) {
            assertThat(JobParameterBean.parameterFieldFor(jobType)).as(jobType.name()).isNotBlank();
        }
    }

    @Test
    void reportsWhichJobTypesAreSupplied() {
        assertThat(JobParameterBean.builder().build().suppliedJobTypes()).isEmpty();

        JobParameterBean both = JobParameterBean.builder()
                .generateReportParameters(new JobParameterBean.GenerateReportParameters())
                .updateCustomersParameters(new JobParameterBean.UpdateCustomersParameters())
                .build();
        assertThat(both.suppliedJobTypes())
                .isEqualTo(Set.of(JobType.GENERATE_REPORT, JobType.UPDATE_CUSTOMERS));
        assertThat(both.parametersFor(JobType.GENERATE_REPORT)).isSameAs(both.getGenerateReportParameters());
        assertThat(both.parametersFor(JobType.RECONCILE_TRANSACTIONS)).isNull();
    }

    @Test
    void serializesOnlyThePopulatedParametersWithIsoDates() {
        JobParameterBean payload = JobParameterBean.builder()
                .generateReportParameters(JobParameterBean.GenerateReportParameters.builder()
                        .reportType("CUSTOMER_SUMMARY")
                        .startDate(LocalDate.of(2026, 1, 1))
                        .endDate(LocalDate.of(2026, 1, 31))
                        .format("CSV")
                        .requestedBy("admin")
                        .build())
                .build();

        JsonNode json = jsonMapper.valueToTree(payload);

        assertThat(json.propertyNames()).containsExactly("generateReportParameters");
        JsonNode parameters = json.get("generateReportParameters");
        assertThat(parameters.get("startDate").asString()).isEqualTo("2026-01-01");
        assertThat(parameters.has("dateRangeValid")).isFalse();

        JobParameterBean roundTripped = jsonMapper.treeToValue(json, JobParameterBean.class);
        assertThat(roundTripped.getGenerateReportParameters().getEndDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(roundTripped.suppliedJobTypes()).containsExactly(JobType.GENERATE_REPORT);
    }
}
