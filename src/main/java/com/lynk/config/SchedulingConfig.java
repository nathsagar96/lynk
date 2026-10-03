package com.lynk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables the scheduling infrastructure used by the expiry cleanup job.
 * <p>
 * Kept separate from the application class so that the enabling annotations each have a
 * single, explicitly named home.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {}
