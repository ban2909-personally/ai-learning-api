package com.ailearning.platform.mentoring.application.port.out;

public interface MentorTurnMonitor {
    void accepted();

    void rejected(String reason);

    void completed();

    void failed(String reason);
}

