package com.ailearning.platform.community.adapter.in.web.controller;

import com.ailearning.platform.community.api.contract.FriendPage;
import com.ailearning.platform.community.api.contract.FriendshipSummary;
import com.ailearning.platform.community.api.contract.SocialProfileView;
import com.ailearning.platform.community.api.usecase.SocialProfileUseCase;
import com.ailearning.platform.identity.api.contract.ProfileUpdate;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community")
public class SocialProfileController {
    private final SocialProfileUseCase profiles;

    public SocialProfileController(SocialProfileUseCase profiles) {
        this.profiles = profiles;
    }

    private UUID actor(Jwt jwt) {
        return jwt == null ? null : UUID.fromString(jwt.getSubject());
    }

    @GetMapping("/people/{id}/profile")
    SocialProfileView profile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return profiles.profile(actor(jwt), id);
    }

    @PutMapping("/profile")
    SocialProfileView update(@AuthenticationPrincipal Jwt jwt, @RequestBody ProfileUpdate update) {
        return profiles.update(actor(jwt), update);
    }

    @GetMapping("/friends")
    FriendPage friends(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "accepted") String filter,
            @RequestParam(defaultValue = "0") int page) {
        return profiles.connections(actor(jwt), filter, page);
    }

    @PostMapping("/people/{id}/friendship")
    FriendshipSummary request(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return profiles.request(actor(jwt), id);
    }

    @PostMapping("/people/{id}/friendship/accept")
    FriendshipSummary accept(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return profiles.accept(actor(jwt), id);
    }

    @DeleteMapping("/people/{id}/friendship")
    FriendshipSummary remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return profiles.remove(actor(jwt), id);
    }
}
