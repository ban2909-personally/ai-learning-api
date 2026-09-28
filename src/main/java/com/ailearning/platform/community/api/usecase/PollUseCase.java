package com.ailearning.platform.community.api.usecase;

import com.ailearning.platform.community.api.contract.PostView;

import java.util.UUID;

public interface PollUseCase {
    PostView vote(UUID actor, UUID postId, UUID optionId);

    PostView close(UUID actor, UUID postId);
}
