package com.ailearning.platform.identity.application;

import com.ailearning.platform.shared.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class PasswordPolicy {

    public void validate(String password) {
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean fitsBcryptLimit = password.getBytes(StandardCharsets.UTF_8).length <= 72;
        if (!hasLetter || !hasDigit || !fitsBcryptLimit) {
            throw new ApiException(
                    "weak_password",
                    HttpStatus.BAD_REQUEST,
                    "Mật khẩu phải có chữ, số và không vượt quá giới hạn 72 byte của BCrypt."
            );
        }
    }
}
