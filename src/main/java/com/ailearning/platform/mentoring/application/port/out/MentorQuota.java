package com.ailearning.platform.mentoring.application.port.out;

import java.util.UUID;

public interface MentorQuota {
    Decision consume(UUID userId);

    record Decision(boolean allowed, int remaining) {
    }
}
