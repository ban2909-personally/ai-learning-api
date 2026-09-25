package com.ailearning.platform.identity.api.contract;

import com.ailearning.platform.identity.domain.model.ManagedAccount;

import java.util.List;

public record AccountPage(List<ManagedAccount> items, long totalElements, int page, int size) {
    public AccountPage {
        items = List.copyOf(items);
    }
}
