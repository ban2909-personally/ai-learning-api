package com.ailearning.platform.identity.application;

import com.ailearning.platform.identity.api.AuthResponse;
import com.ailearning.platform.identity.api.LoginRequest;
import com.ailearning.platform.identity.api.RegisterRequest;
import com.ailearning.platform.identity.api.UserResponse;
import com.ailearning.platform.identity.domain.RefreshSessionEntity;
import com.ailearning.platform.identity.domain.RoleEntity;
import com.ailearning.platform.identity.domain.UserEntity;
import com.ailearning.platform.identity.infrastructure.RefreshSessionRepository;
import com.ailearning.platform.identity.infrastructure.RoleRepository;
import com.ailearning.platform.identity.infrastructure.UserRepository;
import com.ailearning.platform.shared.error.ApiException;
import com.ailearning.platform.shared.security.JwtTokenService;
import com.ailearning.platform.shared.security.SecurityProperties;
import com.ailearning.platform.shared.security.TokenHasher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtTokenService jwtTokenService;
    private final TokenHasher tokenHasher;
    private final SecurityProperties properties;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RefreshSessionRepository refreshSessionRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            JwtTokenService jwtTokenService,
            TokenHasher tokenHasher,
            SecurityProperties properties,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.jwtTokenService = jwtTokenService;
        this.tokenHasher = tokenHasher;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException("email_already_exists", HttpStatus.CONFLICT, "Email đã được sử dụng.");
        }
        passwordPolicy.validate(request.password());
        RoleEntity studentRole = roleRepository.findByCode("STUDENT")
                .orElseThrow(() -> new IllegalStateException("STUDENT role was not seeded"));
        Instant now = clock.instant();
        UserEntity user = new UserEntity(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                studentRole,
                now
        );
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException("email_already_exists", HttpStatus.CONFLICT, "Email đã được sử dụng.");
        }
        return createSession(user, now);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new ApiException("account_disabled", HttpStatus.FORBIDDEN, "Tài khoản đã bị vô hiệu hóa.");
        }
        return createSession(user, clock.instant());
    }

    @Transactional
    public AuthResult refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshSessionEntity currentSession = refreshSessionRepository
                .findByTokenHashAndRevokedAtIsNull(tokenHasher.sha256(refreshToken))
                .orElseThrow(this::invalidRefreshToken);
        if (currentSession.isExpiredAt(now)) {
            currentSession.revoke(now);
            throw invalidRefreshToken();
        }
        if (!"ACTIVE".equals(currentSession.getUser().getStatus())) {
            currentSession.revoke(now);
            throw new ApiException("account_disabled", HttpStatus.FORBIDDEN, "Tài khoản đã bị vô hiệu hóa.");
        }
        currentSession.revoke(now);
        return createSession(currentSession.getUser(), now);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshSessionRepository.findByTokenHashAndRevokedAtIsNull(tokenHasher.sha256(refreshToken))
                .ifPresent(session -> session.revoke(clock.instant()));
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("user_not_found", HttpStatus.NOT_FOUND, "Không tìm thấy người dùng."));
        return UserResponse.from(user);
    }

    private AuthResult createSession(UserEntity user, Instant now) {
        byte[] randomBytes = new byte[48];
        SECURE_RANDOM.nextBytes(randomBytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        refreshSessionRepository.save(new RefreshSessionEntity(
                UUID.randomUUID(),
                user,
                tokenHasher.sha256(refreshToken),
                now.plus(properties.refreshTokenTtl()),
                now
        ));
        AuthResponse response = new AuthResponse(
                jwtTokenService.createAccessToken(user),
                "Bearer",
                properties.accessTokenTtl().toSeconds(),
                UserResponse.from(user)
        );
        return new AuthResult(response, refreshToken, properties.refreshTokenTtl());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ApiException invalidCredentials() {
        return new ApiException("invalid_credentials", HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng.");
    }

    private ApiException invalidRefreshToken() {
        return new ApiException("invalid_refresh_token", HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.");
    }
}
