package com.ailearning.platform.community.adapter.in.web.controller;

import com.ailearning.platform.community.api.contract.DiscoveryPage;
import com.ailearning.platform.community.api.usecase.CommunityDiscoveryUseCase;
import com.ailearning.platform.identity.api.contract.PublicProfile;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community")
public class CommunityDiscoveryController {
    private final CommunityDiscoveryUseCase discovery;

    public CommunityDiscoveryController(CommunityDiscoveryUseCase discovery) {
        this.discovery = discovery;
    }

    @GetMapping("/search")
    DiscoveryPage search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page) {
        return discovery.search(jwt == null ? null : UUID.fromString(jwt.getSubject()), q, page);
    }

    @GetMapping("/people/{id}")
    PublicProfile profile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return discovery.profile(jwt == null ? null : UUID.fromString(jwt.getSubject()), id);
    }
}
