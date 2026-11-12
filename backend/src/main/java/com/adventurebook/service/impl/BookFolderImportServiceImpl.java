package com.adventurebook.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.adventurebook.config.BookImportProperties;
import com.adventurebook.dto.BookImportResultDTO;
import com.adventurebook.exception.BookValidationError;
import com.adventurebook.service.BookFolderImportService;
import com.adventurebook.service.BookImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import static com.adventurebook.service.util.BookImportUtils.archive;
import static com.adventurebook.service.util.BookImportUtils.readyFiles;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "import.enabled", havingValue = "true", matchIfMissing = true)
public class BookFolderImportServiceImpl implements BookFolderImportService {

    private final BookImportService importService;
    private final BookImportProperties properties;
    private final JsonMapper mapper;
    private final AtomicBoolean scanning = new AtomicBoolean();

    @Override
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "${import.scan-interval-ms}",
            initialDelayString = "${import.scan-interval-ms}")
    public void scan() {
        if (!scanning.compareAndSet(false, true)) {
            return;
        }
        try {
            Path directory = Path.of(properties.directory());
            Path ready = Files.createDirectories(directory.resolve("ready"));
            Path added = Files.createDirectories(directory.resolve("added"));
            Path failed = Files.createDirectories(directory.resolve("failed"));
            for (Path file : readyFiles(ready)) {
                try {
                    process(file, added, failed);
                } catch (IOException | RuntimeException exception) {
                    log.error("Could not process {}; it remains available for retry", file, exception);
                }
            }
        } catch (IOException exception) {
            log.error("Could not scan book directory {}", properties.directory(), exception);
        } finally {
            scanning.set(false);
        }
    }

    private void process(Path file, Path added, Path failed) throws IOException {
        BookImportResultDTO result;
        try {
            result = importService.importBook(file.getFileName().toString(), Files.readAllBytes(file));
        } catch (DataIntegrityViolationException exception) {
            Path destination = archive(file, failed, List.of(new BookValidationError("$database",
                    exception.getMostSpecificCause().getMessage())), mapper);
            log.warn("Book file {} failed a database constraint; archived to {}", file.getFileName(), destination);
            return;
        }
        Path destination = switch (result.status()) {
            case IMPORTED, DUPLICATE -> archive(file, added, List.of(), mapper);
            case REJECTED -> archive(file, failed, result.errors(), mapper);
        };
        log.info("Book file {}: {}; archived to {}", file.getFileName(), result.status(), destination);
    }
}
