package com.adventurebook.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.adventurebook.repository.BookRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileSystemUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "import.enabled=true", "import.scan-interval-ms=100",
        "spring.datasource.url=jdbc:h2:mem:book-folder-scheduling;DB_CLOSE_DELAY=-1"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
class BookFolderSchedulingTests {

    private static final String VALID = """
            {"title":"Startup","author":"Alex","difficulty":"EASY","sections":[
              {"id":1,"text":"Start","type":"BEGIN","options":[{"description":"End","gotoId":2}]},
              {"id":2,"text":"End","type":"END"}]}
            """;

    private static Path directory;

    @Autowired
    private BookRepository books;

    @Autowired
    private ThreadPoolTaskScheduler scheduler;

    @DynamicPropertySource
    static void inbox(DynamicPropertyRegistry registry) throws IOException {
        directory = Files.createTempDirectory("book-scheduling-");
        Files.createDirectory(directory.resolve("ready"));
        Files.writeString(directory.resolve("ready/startup.json"), VALID);
        registry.add("import.directory", directory::toString);
    }

    @Test
    void processesStartupFilesAndNewFilesOnSubsequentScheduledScans() throws Exception {
        assertThat(directory.resolve("added")).isDirectory();
        assertThat(directory.resolve("failed")).isDirectory();
        assertThat(directory.resolve("ready/startup.json")).doesNotExist();
        assertThat(books.count()).isEqualTo(1);
        Path next = Files.writeString(directory.resolve("ready/next.json.tmp"), VALID.replace("Startup", "Later"));
        Files.move(next, directory.resolve("ready/next.json"));

        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline && Files.exists(directory.resolve("ready/next.json"))) {
            Thread.sleep(25);
        }

        assertThat(directory.resolve("ready/next.json")).doesNotExist();
        assertThat(books.count()).isEqualTo(2);
    }

    @AfterAll
    void stopSchedulingAndDeleteIsolatedInbox() throws Exception {
        var executor = scheduler.getScheduledExecutor();
        scheduler.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        FileSystemUtils.deleteRecursively(directory);
    }
}
