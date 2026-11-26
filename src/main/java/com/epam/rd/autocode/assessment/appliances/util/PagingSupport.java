package com.epam.rd.autocode.assessment.appliances.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

/**
 * Turns the page, size and sort parameters from the URL into a safe Pageable.
 *
 * Nothing from the URL is trusted: the page cannot be negative, the size must be one of the allowed
 * values, and the sort field must be on the controller's whitelist. (Sorting by an arbitrary property
 * name would otherwise crash with a 500 error or expose internal field names.)
 */
public final class PagingSupport {

    public static final List<Integer> PAGE_SIZES = List.of(5, 10, 20, 50);
    public static final int DEFAULT_SIZE = 10;

    private PagingSupport() {
    }

    public static Pageable pageable(int page, int size, String sortField, String sortDir,
                                    Set<String> allowedFields, String defaultField) {
        int safePage = Math.max(page, 0);
        int safeSize = PAGE_SIZES.contains(size) ? size : DEFAULT_SIZE;
        String field = sortField != null && allowedFields.contains(sortField) ? sortField : defaultField;
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;

        Sort sort = Sort.by(direction, field);
        if (!"id".equals(field) && allowedFields.contains("id")) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, "id")); // stable order between pages
        }
        return PageRequest.of(safePage, safeSize, sort);
    }

    /** The field the result is sorted by (for the sort arrows in the table header). */
    public static String sortField(Pageable pageable) {
        return pageable.getSort().iterator().next().getProperty();
    }

    /** "asc" or "desc". */
    public static String sortDir(Pageable pageable) {
        return pageable.getSort().iterator().next().getDirection().isDescending() ? "desc" : "asc";
    }
}
