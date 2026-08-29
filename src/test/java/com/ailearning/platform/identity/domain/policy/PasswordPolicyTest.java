package com.ailearning.platform.identity.domain.policy;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsPasswordContainingLettersAndDigits() {
        assertThatCode(() -> policy.validate("learning2026")).doesNotThrowAnyException();
    }

    @Test
    void rejectsPasswordWithoutDigits() {
        assertThatThrownBy(() -> policy.validate("onlyletters"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chữ, số");
    }
}
