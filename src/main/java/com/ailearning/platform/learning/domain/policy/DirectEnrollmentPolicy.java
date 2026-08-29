package com.ailearning.platform.learning.domain.policy;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.math.BigDecimal;

public class DirectEnrollmentPolicy {
    public void ensureAllowed(BigDecimal price) {
        if (price.signum() > 0) {
            throw new BusinessException(
                    "payment_required",
                    ErrorType.CONFLICT,
                    "Khóa học trả phí cần hoàn tất thanh toán trước khi ghi danh."
            );
        }
    }
}
