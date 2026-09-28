package com.ailearning.platform.community.api.usecase;

import com.ailearning.platform.community.api.contract.CommunityMediaRead;
import com.ailearning.platform.community.api.contract.CommunityMediaUpload;
import com.ailearning.platform.community.api.contract.PostView;
import com.ailearning.platform.community.domain.model.MediaByteRange;

import java.util.UUID;

public interface CommunityMediaUseCase {
    PostView publish(UUID actor, UUID spaceId, String body, CommunityMediaUpload upload);

    CommunityMediaRead read(UUID viewer, UUID postId, MediaByteRange range, boolean head);
}
