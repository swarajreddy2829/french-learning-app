package com.example.frenchlearning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application entry point for the French Learning Backend.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FrenchLearningApplication {

    public static void main(String[] args) {
        SpringApplication.run(FrenchLearningApplication.class, args);
    }
}
