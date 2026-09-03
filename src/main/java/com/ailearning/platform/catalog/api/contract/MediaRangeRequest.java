package com.ailearning.platform.catalog.api.contract;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

public record MediaRangeRequest(Long start, Long end, Long suffixLength) {
    public MediaRangeRequest {
        int modes = start == null ? 0 : 1;
        modes += suffixLength == null ? 0 : 1;
        if (modes > 1 || end != null && start == null) {
            throw invalidRange();
        }
    }

    public static MediaRangeRequest full() {
        return new MediaRangeRequest(null, null, null);
    }

    public static MediaRangeRequest between(long start, long end) {
        return new MediaRangeRequest(start, end, null);
    }

    public static MediaRangeRequest from(long start) {
        return new MediaRangeRequest(start, null, null);
    }

    public static MediaRangeRequest suffix(long length) {
        return new MediaRangeRequest(null, null, length);
    }

    public ResolvedMediaRange resolve(long totalLength) {
        if (totalLength <= 0) {
            throw unsatisfiedRange();
        }
        if (suffixLength != null) {
            if (suffixLength <= 0) {
                throw invalidRange();
            }
            long length = Math.min(suffixLength, totalLength);
            return new ResolvedMediaRange(totalLength - length, length);
        }
        if (start == null) {
            return new ResolvedMediaRange(0, totalLength);
        }
        if (start < 0 || start >= totalLength || end != null && end < start) {
            throw unsatisfiedRange();
        }
        long resolvedEnd = end == null ? totalLength - 1 : Math.min(end, totalLength - 1);
        return new ResolvedMediaRange(start, resolvedEnd - start + 1);
    }

    private static BusinessException invalidRange() {
        return new BusinessException(
                "invalid_media_range",
                ErrorType.RANGE_NOT_SATISFIABLE,
                "Khoảng dữ liệu media không hợp lệ."
        );
    }

    private static BusinessException unsatisfiedRange() {
        return new BusinessException(
                "media_range_not_satisfiable",
                ErrorType.RANGE_NOT_SATISFIABLE,
                "Khoảng dữ liệu media nằm ngoài kích thước tệp."
        );
    }
}
