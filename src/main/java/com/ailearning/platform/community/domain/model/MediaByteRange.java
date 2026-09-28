package com.ailearning.platform.community.domain.model;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

public record MediaByteRange(Long start, Long end, Long suffix) {
    public long[] resolve(long size) {
        if (size <= 0) throw invalid();
        if (suffix != null) {
            if (start != null || end != null || suffix <= 0) throw invalid();
            return new long[] {Math.max(0, size - suffix), Math.min(size, suffix)};
        }
        long first = start == null ? 0 : start;
        long last = end == null ? size - 1 : Math.min(end, size - 1);
        if (size <= 0 || first < 0 || first >= size || last < first) throw invalid();
        return new long[] {first, last - first + 1};
    }

    private BusinessException invalid() {
        return new BusinessException(
                "invalid_media_range",
                ErrorType.RANGE_NOT_SATISFIABLE,
                "Vùng nội dung không hợp lệ.");
    }
}
