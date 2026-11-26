package com.epam.rd.autocode.assessment.appliances.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PagingSupportTest {

    private static final Set<String> FIELDS = Set.of("id", "name", "price");

    private Pageable pageable(int page, int size, String field, String dir) {
        return PagingSupport.pageable(page, size, field, dir, FIELDS, "name");
    }

    @Test
    void validValues_areUsedAsGiven() {
        Pageable pageable = pageable(2, 20, "price", "desc");

        assertEquals(2, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("price").getDirection());
    }

    @Test
    void negativePage_becomesZero() {
        assertEquals(0, pageable(-5, 10, "name", "asc").getPageNumber());
    }

    @Test
    void sizeThatIsNotAllowed_becomesTheDefault() {
        assertEquals(PagingSupport.DEFAULT_SIZE, pageable(0, 7, "name", "asc").getPageSize());
        assertEquals(PagingSupport.DEFAULT_SIZE, pageable(0, 100000, "name", "asc").getPageSize());
        assertEquals(PagingSupport.DEFAULT_SIZE, pageable(0, 0, "name", "asc").getPageSize());
    }

    @Test
    void everyAllowedSize_isAccepted() {
        for (int size : PagingSupport.PAGE_SIZES) {
            assertEquals(size, pageable(0, size, "name", "asc").getPageSize());
        }
    }

    @Test
    void unknownSortField_fallsBackToTheDefault() {
        Pageable pageable = pageable(0, 10, "password; DROP TABLE client", "asc");

        assertEquals("name", PagingSupport.sortField(pageable));
        assertNull(pageable.getSort().getOrderFor("password; DROP TABLE client"));
    }

    @Test
    void nullSortField_fallsBackToTheDefault() {
        assertEquals("name", PagingSupport.sortField(pageable(0, 10, null, "asc")));
    }

    @Test
    void unknownDirection_meansAscending() {
        assertEquals("asc", PagingSupport.sortDir(pageable(0, 10, "name", "sideways")));
        assertEquals("asc", PagingSupport.sortDir(pageable(0, 10, "name", null)));
    }

    @Test
    void directionIsCaseInsensitive() {
        assertEquals("desc", PagingSupport.sortDir(pageable(0, 10, "name", "DESC")));
    }

    @Test
    void idIsAddedAsTieBreaker_soPagesAreStable() {
        Pageable pageable = pageable(0, 10, "name", "desc");

        assertNotNull(pageable.getSort().getOrderFor("id"));
        assertEquals(Sort.Direction.ASC, pageable.getSort().getOrderFor("id").getDirection());
        assertEquals("name", PagingSupport.sortField(pageable)); // the chosen field stays first
    }

    @Test
    void sortingById_hasNoSecondOrder() {
        assertEquals(1, pageable(0, 10, "id", "desc").getSort().stream().count());
    }

    @Test
    void withoutIdOnTheWhitelist_thereIsNoTieBreaker() {
        Pageable pageable = PagingSupport.pageable(0, 10, "name", "asc", Set.of("name"), "name");

        assertEquals(1, pageable.getSort().stream().count());
    }
}
