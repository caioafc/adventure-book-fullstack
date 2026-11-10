package com.adventurebook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("import")
public record BookImportProperties(String directory) {
}
