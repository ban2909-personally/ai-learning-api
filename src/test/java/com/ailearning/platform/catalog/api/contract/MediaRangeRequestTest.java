package com.ailearning.platform.catalog.api.contract;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaRangeRequestTest {
    @Test
    void resolvesFullRange() {
        assertEquals(new ResolvedMediaRange(0, 1_000), MediaRangeRequest.full().resolve(1_000));
    }

    @Test
    void resolvesBoundedAndOpenEndedRanges() {
        assertEquals(new ResolvedMediaRange(100, 101), MediaRangeRequest.between(100, 200).resolve(1_000));
        assertEquals(new ResolvedMediaRange(900, 100), MediaRangeRequest.from(900).resolve(1_000));
    }

    @Test
    void clampsEndToObjectLength() {
        assertEquals(new ResolvedMediaRange(900, 100), MediaRangeRequest.between(900, 2_000).resolve(1_000));
    }

    @Test
    void resolvesSuffixRange() {
        assertEquals(new ResolvedMediaRange(800, 200), MediaRangeRequest.suffix(200).resolve(1_000));
        assertEquals(new ResolvedMediaRange(0, 1_000), MediaRangeRequest.suffix(2_000).resolve(1_000));
    }

    @Test
    void rejectsUnsatisfiedRange() {
        BusinessException error = assertThrows(BusinessException.class, () ->
                MediaRangeRequest.from(1_000).resolve(1_000));

        assertEquals("media_range_not_satisfiable", error.code());
    }

    @Test
    void rejectsInvalidRangeShape() {
        assertEquals("media_range_not_satisfiable", assertThrows(BusinessException.class, () ->
                MediaRangeRequest.between(200, 100).resolve(1_000)).code());
        assertEquals("invalid_media_range", assertThrows(BusinessException.class, () ->
                MediaRangeRequest.suffix(0).resolve(1_000)).code());
    }
}
