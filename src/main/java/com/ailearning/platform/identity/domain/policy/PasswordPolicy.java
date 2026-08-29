package com.ailearning.platform.identity.domain.policy;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.nio.charset.StandardCharsets;

public class PasswordPolicy {

    public void validate(String password) {
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean fitsBcryptLimit = password.getBytes(StandardCharsets.UTF_8).length <= 72;
        if (!hasLetter || !hasDigit || !fitsBcryptLimit) {
            throw new BusinessException(
                    "weak_password",
                    ErrorType.BAD_REQUEST,
                    "Mật khẩu phải có chữ, số và không vượt quá giới hạn 72 byte của BCrypt."
            );
        }
    }
}
