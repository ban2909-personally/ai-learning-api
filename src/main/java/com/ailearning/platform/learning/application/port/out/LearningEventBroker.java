package com.ailearning.platform.learning.application.port.out;

public interface LearningEventBroker {
    void publish(LearningEventMessage message);
}
