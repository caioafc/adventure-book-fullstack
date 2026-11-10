package com.adventurebook;

import com.adventurebook.config.BookImportProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(BookImportProperties.class)
public class AdventureBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdventureBookApplication.class, args);
    }
}
