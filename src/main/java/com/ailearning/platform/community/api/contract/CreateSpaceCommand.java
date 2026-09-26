package com.ailearning.platform.community.api.contract;

import com.ailearning.platform.community.domain.model.SpaceKind;
import com.ailearning.platform.community.domain.model.SpaceVisibility;

public record CreateSpaceCommand(
        String name, String description, SpaceKind kind, SpaceVisibility visibility) {}
