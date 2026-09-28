package com.ailearning.platform.community.api.contract;

import java.util.UUID;

public record PollOptionView(UUID id, String label, long votes) {}
