package com.ailearning.platform.identity.adapter.out.security;

import com.ailearning.platform.identity.application.port.out.PasswordCodec;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SpringPasswordCodec implements PasswordCodec {
    private final PasswordEncoder delegate;

    public SpringPasswordCodec(PasswordEncoder delegate) { this.delegate = delegate; }
    @Override public String encode(String rawPassword) { return delegate.encode(rawPassword); }
    @Override public boolean matches(String rawPassword, String encodedPassword) { return delegate.matches(rawPassword, encodedPassword); }
}
