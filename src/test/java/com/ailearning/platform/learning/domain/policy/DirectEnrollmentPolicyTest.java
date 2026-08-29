package com.ailearning.platform.learning.domain.policy;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DirectEnrollmentPolicyTest {
    private final DirectEnrollmentPolicy policy = new DirectEnrollmentPolicy();

    @Test
    void allowsFreeCourse() {
        assertThatCode(() -> policy.ensureAllowed(BigDecimal.ZERO)).doesNotThrowAnyException();
    }

    @Test
    void requiresPaymentForPaidCourse() {
        assertThatThrownBy(() -> policy.ensureAllowed(new BigDecimal("199000")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("payment_required");
                    assertThat(exception.type()).isEqualTo(com.ailearning.platform.sharedkernel.error.ErrorType.CONFLICT);
                });
    }
}
