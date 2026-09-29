package com.ailearning.platform.community.application.service.impl;

import com.ailearning.platform.community.api.contract.DiscoveryPage;
import com.ailearning.platform.community.api.usecase.CommunityDiscoveryUseCase;
import com.ailearning.platform.community.application.port.out.CommunityStore;
import com.ailearning.platform.identity.api.contract.PublicProfile;
import com.ailearning.platform.identity.api.usecase.access.AccountAccess;
import com.ailearning.platform.identity.api.usecase.access.PublicProfileLookup;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.List;
import java.util.UUID;

public class CommunityDiscoveryService implements CommunityDiscoveryUseCase {
    private final CommunityStore spaces;
    private final PublicProfileLookup profiles;
    private final AccountAccess accounts;

    public CommunityDiscoveryService(
            CommunityStore spaces, PublicProfileLookup profiles, AccountAccess accounts) {
        this.spaces = spaces;
        this.profiles = profiles;
        this.accounts = accounts;
    }

    @Override
    public DiscoveryPage search(UUID viewer, String query, int page) {
        if (viewer != null) accounts.requireActive(viewer);
        String term = query == null ? "" : query.strip();
        if (term.length() > 120 || page < 0 || page > 100) {
            throw new BusinessException(
                    "invalid_community_search",
                    ErrorType.BAD_REQUEST,
                    "Từ khóa tối đa 120 ký tự và trang tìm kiếm từ 0 đến 100.");
        }
        if (term.isEmpty()) return new DiscoveryPage(List.of(), List.of(), false, false, page);
        var foundSpaces = spaces.searchSpaces(term, viewer, page, 21);
        var people = profiles.search(term, page);
        return new DiscoveryPage(
                foundSpaces.stream().limit(20).toList(),
                people.stream().limit(20).toList(),
                foundSpaces.size() > 20,
                people.size() > 20,
                page);
    }

    @Override
    public PublicProfile profile(UUID viewer, UUID id) {
        if (viewer != null) accounts.requireActive(viewer);
        return profiles.profile(id);
    }
}
