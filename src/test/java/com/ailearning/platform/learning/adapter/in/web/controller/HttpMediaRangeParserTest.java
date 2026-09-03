package com.ailearning.platform.learning.adapter.in.web.controller;

import com.ailearning.platform.catalog.api.contract.MediaRangeRequest;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpMediaRangeParserTest {
    @Test
    void parsesSupportedSingleRanges() {
        assertEquals(MediaRangeRequest.full(), HttpMediaRangeParser.parse(null));
        assertEquals(MediaRangeRequest.between(10, 20), HttpMediaRangeParser.parse("bytes=10-20"));
        assertEquals(MediaRangeRequest.from(10), HttpMediaRangeParser.parse("bytes=10-"));
        assertEquals(MediaRangeRequest.suffix(20), HttpMediaRangeParser.parse("bytes=-20"));
    }

    @Test
    void rejectsMalformedAndMultipleRanges() {
        assertThrows(BusinessException.class, () -> HttpMediaRangeParser.parse("items=0-10"));
        assertThrows(BusinessException.class, () -> HttpMediaRangeParser.parse("bytes=0-1,4-5"));
        assertThrows(BusinessException.class, () -> HttpMediaRangeParser.parse("bytes=abc-def"));
    }
}
