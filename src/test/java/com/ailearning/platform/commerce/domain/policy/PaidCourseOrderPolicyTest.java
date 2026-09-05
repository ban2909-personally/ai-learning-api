package com.ailearning.platform.commerce.domain.policy;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaidCourseOrderPolicyTest {
    private final PaidCourseOrderPolicy policy = new PaidCourseOrderPolicy();

    @Test
    void acceptsAPaidCourseWithoutExistingAccess() {
        assertThatCode(() -> {
            policy.ensurePaid(new BigDecimal("100000"));
            policy.ensureNotEnrolled(false);
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsAFreeCourseAndExistingEnrollmentWithStableCodes() {
        assertThatThrownBy(() -> policy.ensurePaid(BigDecimal.ZERO))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("course_is_free"));
        assertThatThrownBy(() -> policy.ensureNotEnrolled(true))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("course_already_enrolled"));
    }
}
