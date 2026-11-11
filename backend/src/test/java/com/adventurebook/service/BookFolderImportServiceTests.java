package com.adventurebook.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.adventurebook.config.BookImportProperties;
import com.adventurebook.dto.BookImportFailureDTO;
import com.adventurebook.dto.BookImportResultDTO;
import com.adventurebook.repository.BookRepository;
import com.adventurebook.service.impl.BookFolderImportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.url=jdbc:h2:mem:book-folder;DB_CLOSE_DELAY=-1")
class BookFolderImportServiceTests {

    private static final String VALID = """
            {"title":"The Door","author":"Alex","difficulty":"EASY","sections":[
              {"id":1,"text":"A door","type":"BEGIN","options":[
                {"description":"Leave","gotoId":"2"}]},
              {"id":2,"text":"Free","type":"END"}]}
            """;

    @TempDir
    Path directory;

    @Autowired
    private BookImportService importService;

    @Autowired
    private BookRepository books;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper mapper;

    @Autowired
    private ApplicationContext context;

    private BookFolderImportService scanner;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM book_options");
        jdbc.update("DELETE FROM book_sections");
        jdbc.update("DELETE FROM books");
        scanner = scanner(importService);
        scanner.scan();
    }

    @Test
    void importsAValidFileAndMovesItToAdded() throws Exception {
        Path source = incoming("door.json", VALID);

        scanner.scan();

        assertThat(source).doesNotExist();
        assertThat(files("added")).singleElement().satisfies(archive -> {
            try {
                assertThat(Files.readString(archive)).isEqualTo(VALID);
            } catch (IOException exception) {
                throw new AssertionError(exception);
            }
        });
        assertThat(files("failed")).isEmpty();
        assertThat(books.count()).isEqualTo(1);
    }

    @Test
    void archivesDuplicateFilesWithoutInsertingDuplicateBooks() throws Exception {
        incoming("first.json", VALID);
        incoming("second.json", VALID);

        scanner.scan();

        assertThat(files("ready")).isEmpty();
        assertThat(files("added")).hasSize(2);
        assertThat(books.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{broken"})
    void archivesInvalidContentWithDiagnosticsAndContinuesTheBatch(String invalid) throws Exception {
        incoming("invalid.json", invalid);
        incoming("valid.json", VALID);

        scanner.scan();

        assertThat(files("ready")).isEmpty();
        assertThat(files("added")).hasSize(1);
        assertThat(books.count()).isEqualTo(1);
        Path reportPath = files("failed").stream()
                .filter(file -> file.getFileName().toString().endsWith("-report.json")).findFirst().orElseThrow();
        BookImportFailureDTO report = mapper.readValue(Files.readAllBytes(reportPath), BookImportFailureDTO.class);
        assertThat(report.sourceFilename()).isEqualTo("invalid.json");
        assertThat(reportPath.getFileName().toString())
                .isEqualTo(report.archivedFilename().replace(".json", "-report.json"));
        assertThat(report.errors()).isNotEmpty();
        assertThat(Files.readString(directory.resolve("failed").resolve(report.archivedFilename())))
                .isEqualTo(invalid);
    }

    @Test
    void neverOverwritesExistingArchivesWithTheSameOriginalFilename() throws Exception {
        Path previousArchive = directory.resolve("added/door.json");
        Files.writeString(previousArchive, "previous archive");
        incoming("door.json", VALID);

        scanner.scan();

        assertThat(Files.readString(previousArchive)).isEqualTo("previous archive");
        assertThat(files("added")).hasSize(2);
    }

    @Test
    void retainsTransientFailuresAndProcessesOtherFiles() throws Exception {
        BookImportService importer = delegatingImporter();
        doThrow(new TransientDataAccessResourceException("Simulated outage"))
                .when(importer).importBook(eq("retry.json"), any());
        Path retry = incoming("retry.json", VALID);
        incoming("other.json", VALID.replace("The Door", "Another Door"));

        scanner(importer).scan();

        assertThat(retry).exists();
        assertThat(files("added")).hasSize(1);
        assertThat(files("failed")).isEmpty();
        assertThat(books.count()).isEqualTo(1);
        scanner.scan();
        assertThat(retry).doesNotExist();
        assertThat(books.count()).isEqualTo(2);
    }

    @Test
    void archivesDatabaseConstraintFailuresWithDiagnostics() throws Exception {
        BookImportService importer = delegatingImporter();
        doThrow(new DataIntegrityViolationException("Simulated constraint failure"))
                .when(importer).importBook(eq("constraint.json"), any());
        incoming("constraint.json", VALID);
        incoming("other.json", VALID);

        scanner(importer).scan();

        assertThat(files("ready")).isEmpty();
        assertThat(files("failed")).hasSize(2);
        assertThat(files("added")).hasSize(1);
        assertThat(books.count()).isEqualTo(1);
    }

    @Test
    void retriesAnArchiveFailureWithoutReinsertingTheCommittedBook() throws Exception {
        BookImportService importer = importerBlockingArchive("added");
        Path source = incoming("door.json", VALID);

        scanner(importer).scan();

        assertThat(source).exists();
        assertThat(books.count()).isEqualTo(1);
        restoreDirectory("added");
        scanner.scan();
        assertThat(source).doesNotExist();
        assertThat(files("added")).hasSize(1);
        assertThat(books.count()).isEqualTo(1);
    }

    @Test
    void retainsRejectedFilesWhenWritingTheReportFails() throws Exception {
        Path source = incoming("invalid.json", "{broken");

        scanner(importerBlockingArchive("failed")).scan();

        assertThat(source).exists();
        restoreDirectory("failed");
        scanner.scan();
        assertThat(source).doesNotExist();
        assertThat(files("failed")).hasSize(2);
    }

    @Test
    void ignoresTemporaryFilesNonJsonFilesAndDirectories() throws Exception {
        incoming("draft.json.tmp", VALID);
        incoming("notes.txt", VALID);
        Files.createDirectory(directory.resolve("ready/subdirectory.json"));

        scanner.scan();

        assertThat(files("ready")).hasSize(2);
        assertThat(files("added")).isEmpty();
        assertThat(books.count()).isZero();
    }

    @Test
    void doesNotOverlapScans() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BookImportService importer = mock(BookImportService.class);
        when(importer.importBook(anyString(), any())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test release");
            }
            return importService.importBook(invocation.getArgument(0), invocation.getArgument(1));
        });
        incoming("door.json", VALID);
        BookFolderImportService sharedScanner = scanner(importer);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(sharedScanner::scan);
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                executor.submit(sharedScanner::scan).get(2, TimeUnit.SECONDS);
                verify(importer, times(1)).importBook(anyString(), any());
            } finally {
                release.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
        }
        assertThat(books.count()).isEqualTo(1);
        assertThat(files("added")).hasSize(1);
    }

    @Test
    void disablesTheManagedImporterWhenTheFlagIsFalse() {
        assertThat(context.getBeansOfType(BookFolderImportService.class)).isEmpty();
    }

    @Test
    void canScanAgainAfterDirectoryInitializationFails() throws Exception {
        Path source = incoming("door.json", VALID);
        Files.delete(directory.resolve("added"));
        Files.createFile(directory.resolve("added"));

        scanner.scan();

        assertThat(source).exists();
        assertThat(books.count()).isZero();
        restoreDirectory("added");
        scanner.scan();
        assertThat(source).doesNotExist();
        assertThat(books.count()).isEqualTo(1);
    }

    private BookFolderImportService scanner(BookImportService importer) {
        return new BookFolderImportServiceImpl(importer, new BookImportProperties(directory.toString()), mapper);
    }

    private BookImportService delegatingImporter() {
        BookImportService importer = mock(BookImportService.class);
        when(importer.importBook(anyString(), any())).thenAnswer(invocation ->
                importService.importBook(invocation.getArgument(0), invocation.getArgument(1)));
        return importer;
    }

    private BookImportService importerBlockingArchive(String folder) {
        BookImportService importer = mock(BookImportService.class);
        doAnswer(invocation -> {
            BookImportResultDTO result = importService.importBook(invocation.getArgument(0), invocation.getArgument(1));
            Files.delete(directory.resolve(folder));
            Files.createFile(directory.resolve(folder));
            return result;
        }).when(importer).importBook(anyString(), any());
        return importer;
    }

    private Path incoming(String filename, String content) throws IOException {
        return Files.writeString(directory.resolve("ready").resolve(filename), content);
    }

    private List<Path> files(String folder) throws IOException {
        try (var files = Files.list(directory.resolve(folder))) {
            return files.filter(Files::isRegularFile).toList();
        }
    }

    private void restoreDirectory(String folder) throws IOException {
        Files.delete(directory.resolve(folder));
        Files.createDirectory(directory.resolve(folder));
    }
}
