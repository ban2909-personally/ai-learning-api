package com.ailearning.platform.community.domain.policy;

import static org.junit.jupiter.api.Assertions.*;

import com.ailearning.platform.community.domain.model.MediaByteRange;
import com.ailearning.platform.sharedkernel.error.BusinessException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommunityMediaPolicyTest {
    private final CommunityMediaPolicy policy = new CommunityMediaPolicy();
    private final byte[] png = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    @Test
    void strictDecimalLimitAndSupportedHeaders() {
        assertDoesNotThrow(() -> policy.validate("image/png", 9_999_999, png));
        assertThrows(BusinessException.class, () -> policy.validate("image/png", 10_000_000, png));
        assertThrows(BusinessException.class, () -> policy.validate("image/png", 0, png));
        assertDoesNotThrow(
                () ->
                        policy.validate(
                                "image/jpeg", 32, new byte[] {(byte) 255, (byte) 216, (byte) 255}));
        assertDoesNotThrow(
                () ->
                        policy.validate(
                                "image/webp",
                                32,
                                "RIFFxxxxWEBP"
                                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertDoesNotThrow(
                () ->
                        policy.validate(
                                "video/mp4",
                                32,
                                "xxxxftypisomxxxx"
                                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertDoesNotThrow(
                () ->
                        policy.validate(
                                "video/webm", 32, new byte[] {26, 69, (byte) 223, (byte) 163}));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "image/svg+xml",
                "text/html",
                "application/octet-stream",
                "image/jpeg",
                "video/mp4",
                "video/webm",
                "image/webp"
            })
    void rejectsUnsupportedTypesAndMismatchedHeaders(String type) {
        assertThrows(BusinessException.class, () -> policy.validate(type, 32, png));
    }

    @Test
    void resolvesFullClosedOpenAndSuffixRanges() {
        assertArrayEquals(new long[] {0, 100}, new MediaByteRange(null, null, null).resolve(100));
        assertArrayEquals(new long[] {10, 11}, new MediaByteRange(10L, 20L, null).resolve(100));
        assertArrayEquals(new long[] {10, 90}, new MediaByteRange(10L, null, null).resolve(100));
        assertArrayEquals(new long[] {95, 5}, new MediaByteRange(null, null, 5L).resolve(100));
        assertArrayEquals(new long[] {0, 100}, new MediaByteRange(null, null, 1000L).resolve(100));
        assertArrayEquals(new long[] {10, 90}, new MediaByteRange(10L, 1000L, null).resolve(100));
    }

    @Test
    void rejectsInvalidRanges() {
        for (MediaByteRange range :
                java.util.List.of(
                        new MediaByteRange(100L, null, null),
                        new MediaByteRange(-1L, 3L, null),
                        new MediaByteRange(20L, 10L, null),
                        new MediaByteRange(null, null, 0L),
                        new MediaByteRange(1L, null, 3L))) {
            assertThrows(BusinessException.class, () -> range.resolve(100));
        }
    }
}
