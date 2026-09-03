package com.ailearning.platform.learning.adapter.in.web.controller;

import com.ailearning.platform.catalog.api.contract.MediaRangeRequest;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

final class HttpMediaRangeParser {
    private static final String UNIT = "bytes=";

    private HttpMediaRangeParser() {
    }

    static MediaRangeRequest parse(String header) {
        if (header == null || header.isBlank()) {
            return MediaRangeRequest.full();
        }
        if (!header.startsWith(UNIT) || header.indexOf(',') >= 0) {
            throw invalidRange();
        }

        String value = header.substring(UNIT.length()).trim();
        int separator = value.indexOf('-');
        if (separator < 0 || separator != value.lastIndexOf('-')) {
            throw invalidRange();
        }

        String start = value.substring(0, separator).trim();
        String end = value.substring(separator + 1).trim();
        if (start.isEmpty() && end.isEmpty()) {
            throw invalidRange();
        }

        try {
            if (start.isEmpty()) {
                return MediaRangeRequest.suffix(Long.parseLong(end));
            }
            long parsedStart = Long.parseLong(start);
            return end.isEmpty()
                    ? MediaRangeRequest.from(parsedStart)
                    : MediaRangeRequest.between(parsedStart, Long.parseLong(end));
        } catch (NumberFormatException exception) {
            throw invalidRange();
        }
    }

    private static BusinessException invalidRange() {
        return new BusinessException(
                "invalid_media_range",
                ErrorType.RANGE_NOT_SATISFIABLE,
                "Header Range không hợp lệ."
        );
    }
}
