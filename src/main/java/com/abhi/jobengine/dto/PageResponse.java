package com.abhi.jobengine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * One page of results plus pagination metadata. Used instead of serializing Spring Data's
 * {@link Page} directly, whose JSON shape is not a stable API.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Schema(description = "A page of results with pagination metadata")
public class PageResponse<T> {

    private List<T> content;

    @Schema(description = "Zero-based page number", example = "0")
    private int page;

    @Schema(description = "Requested page size", example = "20")
    private int size;

    @Schema(description = "Total number of matching records", example = "42")
    private long totalElements;

    @Schema(description = "Total number of pages", example = "3")
    private int totalPages;

    @Schema(example = "true")
    private boolean hasNext;

    @Schema(example = "false")
    private boolean hasPrevious;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }
}
