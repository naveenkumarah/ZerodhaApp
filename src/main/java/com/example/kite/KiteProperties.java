package com.example.kite;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kite")
public record KiteProperties(String apiKey, String apiSecret, String baseUrl) {}

