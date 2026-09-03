package com.ailearning.platform.identity.adapter.in.web.controller;

import com.ailearning.platform.identity.adapter.in.web.dto.request.LoginRequest;
import com.ailearning.platform.identity.adapter.in.web.dto.request.RegisterRequest;
import com.ailearning.platform.identity.adapter.in.web.dto.response.AuthResponse;
import com.ailearning.platform.identity.adapter.in.web.dto.response.UserResponse;
import com.ailearning.platform.identity.api.contract.AuthSession;
import com.ailearning.platform.identity.api.contract.LoginCommand;
import com.ailearning.platform.identity.api.contract.RegisterCommand;
import com.ailearning.platform.identity.api.usecase.IdentityUseCase;
import com.ailearning.platform.platform.security.SecurityProperties;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String MEDIA_ACCESS_COOKIE = "media_access";
    private final IdentityUseCase identity;
    private final SecurityProperties securityProperties;

    public AuthController(IdentityUseCase identity, SecurityProperties securityProperties) {
        this.identity = identity;
        this.securityProperties = securityProperties;
    }

    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withSession(identity.register(new RegisterCommand(request.email(), request.password(), request.displayName())), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withSession(identity.login(new LoginCommand(request.email(), request.password())), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
        if (token == null || token.isBlank()) throw new BusinessException("missing_refresh_token", ErrorType.UNAUTHORIZED, "Không tìm thấy phiên đăng nhập.");
        return withSession(identity.refresh(token), HttpStatus.OK);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
        if (token != null && !token.isBlank()) identity.logout(token);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .header(HttpHeaders.SET_COOKIE, mediaAccessCookie("", Duration.ZERO).toString())
                .build();
    }

    @GetMapping("/me")
    UserResponse me(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(identity.me(UUID.fromString(jwt.getSubject())));
    }

    private ResponseEntity<AuthResponse> withSession(AuthSession session, HttpStatus status) {
        AuthResponse body = new AuthResponse(session.accessToken(), "Bearer", session.expiresIn(), UserResponse.from(session.user()));
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken(), session.refreshTokenTtl()).toString())
                .header(HttpHeaders.SET_COOKIE, mediaAccessCookie(
                        session.accessToken(),
                        Duration.ofSeconds(session.expiresIn())
                ).toString())
                .body(body);
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true)
                .secure(securityProperties.refreshCookieSecure()).sameSite("Strict")
                .path("/api/v1/auth").maxAge(maxAge).build();
    }

    private ResponseCookie mediaAccessCookie(String value, Duration maxAge) {
        return ResponseCookie.from(MEDIA_ACCESS_COOKIE, value).httpOnly(true)
                .secure(securityProperties.refreshCookieSecure()).sameSite("Strict")
                .path("/api/v1/media").maxAge(maxAge).build();
    }
}
