package com.ailearning.platform.mentoring.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MentorExecutorPropertiesTest {
    @Test
    void acceptsAnOrderedBoundedPool() {
        assertThat(new MentorExecutorProperties(8, 32, 32).hasOrderedPoolSizes()).isTrue();
    }

    @Test
    void rejectsACorePoolLargerThanTheMaximumPool() {
        assertThat(new MentorExecutorProperties(33, 32, 32).hasOrderedPoolSizes()).isFalse();
    }
}
