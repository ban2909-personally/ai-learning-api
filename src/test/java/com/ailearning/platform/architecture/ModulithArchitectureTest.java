package com.ailearning.platform.architecture;

import com.ailearning.platform.AiLearningApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {
    @Test
    void modulesRespectDeclaredBoundariesAndHaveNoCycles() {
        ApplicationModules.of(AiLearningApiApplication.class).verify();
    }
}
