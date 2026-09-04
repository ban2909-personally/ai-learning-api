package com.ailearning.platform.learning.application.port.out;

public interface LearningEventDispatchMonitor {
    void published();

    void failed();
}
