package com.lynk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the lynk URL shortener service.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LynkApplication {

    static void main(String[] args) {
        SpringApplication.run(LynkApplication.class, args);
    }
}
