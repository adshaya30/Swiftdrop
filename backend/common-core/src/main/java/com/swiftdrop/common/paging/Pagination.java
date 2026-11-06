package com.swiftdrop.common.paging;

import java.util.List;

/** List response of all paginated endpoints: items, page, size, totalItems, totalPages. */
public record Pagination<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> Pagination<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = size <= 0 ? 0 : (int) ((totalItems + size - 1) / size);
        return new Pagination<>(List.copyOf(items), page, size, totalItems, totalPages);
    }
}
