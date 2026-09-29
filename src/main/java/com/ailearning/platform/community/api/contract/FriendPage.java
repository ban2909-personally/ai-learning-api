package com.ailearning.platform.community.api.contract;

import java.util.List;

public record FriendPage(List<FriendConnection> people, Integer nextPage) {}
