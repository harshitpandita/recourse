package com.recourse.explain;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "recourse.llm")
public record LlmProperties(boolean enabled, String baseUrl, String model, int timeoutSeconds) {}
