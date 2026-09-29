package com.ailearning.platform.identity.application.service.impl;

import com.ailearning.platform.identity.api.contract.PublicProfile;
import com.ailearning.platform.identity.api.usecase.access.PublicProfileLookup;
import com.ailearning.platform.identity.application.port.out.PublicProfileStore;
import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.util.List;
import java.util.UUID;

public class PublicProfileService implements PublicProfileLookup {
    private final PublicProfileStore store;

    public PublicProfileService(PublicProfileStore store) {
        this.store = store;
    }

    @Override
    public List<PublicProfile> search(String query, int page) {
        String term = query == null ? "" : query.strip();
        if (term.length() > 120 || page < 0 || page > 100) {
            throw new BusinessException(
                    "invalid_profile_search",
                    ErrorType.BAD_REQUEST,
                    "Từ khóa hoặc trang tìm kiếm không hợp lệ.");
        }
        return term.isEmpty() ? List.of() : store.search(term, page);
    }

    @Override
    public PublicProfile profile(UUID id) {
        return store.findActive(id)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        "profile_not_found",
                                        ErrorType.NOT_FOUND,
                                        "Không tìm thấy hồ sơ công khai."));
    }
}
