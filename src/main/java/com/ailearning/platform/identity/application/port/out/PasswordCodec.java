package com.ailearning.platform.identity.application.port.out;

public interface PasswordCodec {
    String encode(String rawPassword);
    boolean matches(String rawPassword, String encodedPassword);
}
