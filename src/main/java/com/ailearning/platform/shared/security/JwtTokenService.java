package com.ailearning.platform.shared.security;

import com.ailearning.platform.identity.domain.RoleEntity;
import com.ailearning.platform.identity.domain.UserEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder jwtEncoder, SecurityProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public String createAccessToken(UserEntity user) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.accessTokenTtl()))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles().stream().map(RoleEntity::getCode).sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
