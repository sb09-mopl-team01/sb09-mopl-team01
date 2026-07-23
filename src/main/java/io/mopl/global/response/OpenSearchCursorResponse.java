package io.mopl.global.response;

import java.util.List;

public record OpenSearchCursorResponse<T>(
    List<T> content,
    List<Object> nextSortValues,
    boolean hasNext,
    long totalCount
) {
}
