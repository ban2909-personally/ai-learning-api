package com.ailearning.platform.identity.application.service.impl;

import com.ailearning.platform.identity.api.contract.AuthSession;
import com.ailearning.platform.identity.api.contract.LoginCommand;
import com.ailearning.platform.identity.api.contract.RegisterCommand;
import com.ailearning.platform.identity.api.contract.UserView;
import com.ailearning.platform.identity.api.usecase.IdentityUseCase;
import com.ailearning.platform.identity.api.usecase.lookup.UserLookup;
import com.ailearning.platform.identity.domain.policy.PasswordPolicy;
import com.ailearning.platform.identity.application.port.out.AccessTokenIssuer;
import com.ailearning.platform.identity.application.port.out.PasswordCodec;
import com.ailearning.platform.identity.application.port.out.RefreshSessionStore;
import com.ailearning.platform.identity.application.port.out.TokenDigest;
import com.ailearning.platform.identity.application.port.out.UserStore;
import com.ailearning.platform.identity.domain.model.RefreshSession;
import com.ailearning.platform.identity.domain.model.User;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;
import org.springframework.dao.DataIntegrityViolationException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

public class IdentityService implements IdentityUseCase, UserLookup {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final UserStore users;
    private final RefreshSessionStore sessions;
    private final PasswordCodec passwords;
    private final PasswordPolicy passwordPolicy;
    private final AccessTokenIssuer tokenIssuer;
    private final TokenDigest tokenDigest;
    private final Clock clock;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public IdentityService(UserStore users, RefreshSessionStore sessions, PasswordCodec passwords,
                           PasswordPolicy passwordPolicy, AccessTokenIssuer tokenIssuer, TokenDigest tokenDigest,
                           Clock clock, Duration accessTokenTtl, Duration refreshTokenTtl) {
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.passwordPolicy = passwordPolicy;
        this.tokenIssuer = tokenIssuer;
        this.tokenDigest = tokenDigest;
        this.clock = clock;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Override
    public AuthSession register(RegisterCommand command) {
        String email = normalizeEmail(command.email());
        if (users.existsByEmail(email)) throw conflictEmail();
        passwordPolicy.validate(command.password());
        Instant now = clock.instant();
        User user;
        try {
            user = users.createStudent(UUID.randomUUID(), email, passwords.encode(command.password()),
                    command.displayName().trim(), now);
        } catch (DataIntegrityViolationException exception) {
            throw conflictEmail();
        }
        return createSession(user, now);
    }

    @Override
    public AuthSession login(LoginCommand command) {
        User user = users.findByEmail(normalizeEmail(command.email())).orElseThrow(this::invalidCredentials);
        if (!passwords.matches(command.password(), user.passwordHash())) throw invalidCredentials();
        ensureActive(user);
        return createSession(user, clock.instant());
    }

    @Override
    public AuthSession refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshSession current = sessions.findActiveForUpdate(tokenDigest.digest(refreshToken))
                .orElseThrow(this::invalidRefreshToken);
        if (current.expiredAt(now)) {
            sessions.revoke(current.id(), now);
            throw invalidRefreshToken();
        }
        ensureActive(current.user());
        sessions.revoke(current.id(), now);
        return createSession(current.user(), now);
    }

    @Override
    public void logout(String refreshToken) {
        sessions.findActiveForUpdate(tokenDigest.digest(refreshToken))
                .ifPresent(session -> sessions.revoke(session.id(), clock.instant()));
    }

    @Override
    public UserView me(UUID userId) {
        return users.findById(userId).map(this::toView)
                .orElseThrow(() -> new BusinessException("user_not_found", ErrorType.NOT_FOUND, "Không tìm thấy người dùng."));
    }

    @Override
    public boolean exists(UUID userId) { return users.existsById(userId); }

    private AuthSession createSession(User user, Instant now) {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.create(UUID.randomUUID(), user.id(), tokenDigest.digest(refreshToken), now.plus(refreshTokenTtl), now);
        return new AuthSession(tokenIssuer.issue(user), accessTokenTtl.toSeconds(), toView(user),
                refreshToken, refreshTokenTtl);
    }

    private void ensureActive(User user) {
        if (!user.active()) throw new BusinessException("account_disabled", ErrorType.FORBIDDEN, "Tài khoản đã bị vô hiệu hóa.");
    }
    private UserView toView(User user) {
        return new UserView(user.id(), user.email(), user.displayName(), new java.util.TreeSet<>(user.roles()));
    }
    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private BusinessException conflictEmail() { return new BusinessException("email_already_exists", ErrorType.CONFLICT, "Email đã được sử dụng."); }
    private BusinessException invalidCredentials() { return new BusinessException("invalid_credentials", ErrorType.UNAUTHORIZED, "Email hoặc mật khẩu không đúng."); }
    private BusinessException invalidRefreshToken() { return new BusinessException("invalid_refresh_token", ErrorType.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn."); }
}
