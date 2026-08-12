package com.ailearning.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiLearningApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiLearningApiApplication.class, args);
    }
}
