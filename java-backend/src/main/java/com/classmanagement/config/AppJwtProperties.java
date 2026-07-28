package com.classmanagement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
public record AppJwtProperties(String secret, Duration expires) {
}
