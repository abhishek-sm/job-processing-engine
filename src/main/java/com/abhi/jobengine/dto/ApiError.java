package com.abhi.jobengine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

/** Error body returned for every failed request. Never contains stack traces or SQL. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Error response")
public class ApiError {

    private Instant timestamp;

    @Schema(example = "400")
    private int status;

    @Schema(example = "Bad Request")
    private String error;

    @Schema(example = "Request validation failed")
    private String message;

    @Schema(example = "/api/v1/jobs")
    private String path;

    /** Per-field problems; only present for validation errors. */
    private List<FieldError> fieldErrors;

    /** A single invalid field. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class FieldError {

        @Schema(example = "payload.generateReportParameters.reportType")
        private String field;

        @Schema(example = "must not be blank")
        private String message;
    }
}
