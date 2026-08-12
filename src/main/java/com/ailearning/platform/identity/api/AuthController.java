package com.ailearning.platform.identity.api;

import com.ailearning.platform.identity.application.AuthResult;
import com.ailearning.platform.identity.application.AuthService;
import com.ailearning.platform.shared.error.ApiException;
import com.ailearning.platform.shared.security.SecurityProperties;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService authService;
    private final SecurityProperties securityProperties;

    public AuthController(AuthService authService, SecurityProperties securityProperties) {
        this.authService = authService;
        this.securityProperties = securityProperties;
    }

    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withSession(authService.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withSession(authService.login(request), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(
                    "missing_refresh_token",
                    HttpStatus.UNAUTHORIZED,
                    "Không tìm thấy phiên đăng nhập."
            );
        }
        return withSession(authService.refresh(refreshToken), HttpStatus.OK);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken
    ) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    @GetMapping("/me")
    UserResponse me(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
        return authService.me(UUID.fromString(jwt.getSubject()));
    }

    private ResponseEntity<AuthResponse> withSession(AuthResult result, HttpStatus status) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookie(result.refreshToken(), result.refreshTokenTtl()).toString())
                .body(result.response());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(securityProperties.refreshCookieSecure())
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }
}
