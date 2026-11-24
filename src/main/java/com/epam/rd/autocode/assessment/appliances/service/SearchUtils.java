package com.epam.rd.autocode.assessment.appliances.service;

/** Small helpers shared by the services. */
public final class SearchUtils {

    private SearchUtils() {
    }

    /** Null-safe search text: the repositories expect a non-null query ("" matches everything). */
    public static String normalize(String query) {
        return query == null ? "" : query.trim();
    }
}
