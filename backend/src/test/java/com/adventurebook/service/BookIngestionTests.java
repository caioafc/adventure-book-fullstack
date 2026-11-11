package com.adventurebook.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import com.adventurebook.dto.BookImportResultDTO;
import com.adventurebook.enums.BookImportStatus;
import com.adventurebook.enums.ConsequenceType;
import com.adventurebook.model.BookOption;
import com.adventurebook.repository.BookOptionRepository;
import com.adventurebook.repository.BookRepository;
import com.adventurebook.repository.BookSectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.url=jdbc:h2:mem:book-ingestion;DB_CLOSE_DELAY=-1")
class BookIngestionTests {

    private static final String VALID = """
            {"title":"The Cave","author":"Alex","difficulty":"MEDIUM","sections":[
              {"id":1,"text":"At the entrance","type":"BEGIN","options":[
                {"description":"Explore","gotoId":"2","consequence":{
                  "type":"LOSE_HEALTH","value":"3","text":"A scratch"}},
                {"description":"Leave","gotoId":3}]},
              {"id":2,"text":"Deep inside","type":"NODE","options":[
                {"description":"Rest and leave","gotoId":3,"consequence":{
                  "type":"GAIN_HEALTH","value":2,"text":"Feeling better"}},
                {"description":"Return","gotoId":1}]},
              {"id":3,"text":"Free","type":"END"}]}
            """;

    @Autowired
    private BookImportService service;

    @Autowired
    private BookRepository books;

    @Autowired
    private BookSectionRepository sections;

    @MockitoSpyBean
    private BookOptionRepository options;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearDatabase() {
        reset(options);
        jdbc.update("DELETE FROM book_options");
        jdbc.update("DELETE FROM book_sections");
        jdbc.update("DELETE FROM books");
    }

    @Test
    @Transactional
    void persistsCompleteBookWithLinksOrderAndConsequences() {
        BookImportResultDTO result = service.importBook("cave.json", content(VALID));

        assertThat(result.status()).isEqualTo(BookImportStatus.IMPORTED);
        assertThat(result.bookId()).isNotNull();
        assertThat(result.contentHash()).matches("[a-f0-9]{64}");
        assertThat(result.errors()).isEmpty();
        assertCounts(1, 3, 4);
        var book = books.findById(result.bookId()).orElseThrow();
        assertThat(book.getTitle()).isEqualTo("The Cave");
        assertThat(book.getSourceFilename()).isEqualTo("cave.json");
        assertThat(book.getContentHash()).isEqualTo(result.contentHash());
        assertThat(book.getImportedAt()).isNotNull();
        var beginning = sections.findByBookIdAndSectionKey(book.getId(), "1").orElseThrow();
        List<BookOption> choices = options.findAllBySectionIdOrderByPositionAsc(beginning.getId());
        assertThat(choices).extracting(BookOption::getDescription).containsExactly("Explore", "Leave");
        assertThat(choices.getFirst().getDestination().getSectionKey()).isEqualTo("2");
        assertThat(choices.getFirst().getConsequenceType()).isEqualTo(ConsequenceType.LOSE_HEALTH);
        assertThat(choices.getFirst().getConsequenceValue()).isEqualTo(3);
        assertThat(choices.getFirst().getConsequenceText()).isEqualTo("A scratch");
        assertThat(choices.get(1).getConsequenceType()).isNull();
        var cave = sections.findByBookIdAndSectionKey(book.getId(), "2").orElseThrow();
        List<BookOption> caveChoices = options.findAllBySectionIdOrderByPositionAsc(cave.getId());
        assertThat(caveChoices.getFirst().getConsequenceType()).isEqualTo(ConsequenceType.GAIN_HEALTH);
        assertThat(caveChoices.get(1).getDestination().getId()).isEqualTo(beginning.getId());
    }

    @Test
    void recognizesIdenticalContentUnderAnotherFilenameWithoutChangingTheStoredBook() {
        BookImportResultDTO first = service.importBook("first.json", content(VALID));
        BookImportResultDTO second = service.importBook("renamed.json", content(VALID));

        assertThat(second.status()).isEqualTo(BookImportStatus.DUPLICATE);
        assertThat(second.bookId()).isEqualTo(first.bookId());
        assertThat(second.contentHash()).isEqualTo(first.contentHash());
        assertThat(books.findById(first.bookId()).orElseThrow().getSourceFilename()).isEqualTo("first.json");
        assertCounts(1, 3, 4);
    }

    @Test
    void importsChangedContentUnderTheSameFilenameAsANewBook() {
        BookImportResultDTO first = service.importBook("cave.json", content(VALID));
        BookImportResultDTO second = service.importBook("cave.json", content(VALID.replace("The Cave", "The Forest")));

        assertThat(second.status()).isEqualTo(BookImportStatus.IMPORTED);
        assertThat(second.bookId()).isNotEqualTo(first.bookId());
        assertThat(second.contentHash()).isNotEqualTo(first.contentHash());
        assertCounts(2, 6, 8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dragon-quest.json", "the-prisoner.json", "crystal-caverns.json", "pirates-jade-sea.json"})
    void rejectsInvalidTestBooksWithoutInsertingAnything(String filename) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/books/" + filename)) {
            assertThat(input).isNotNull();
            BookImportResultDTO result = service.importBook(filename, input.readAllBytes());

            assertThat(result.status()).isEqualTo(BookImportStatus.REJECTED);
            assertThat(result.bookId()).isNull();
            assertThat(result.errors()).isNotEmpty();
            assertCounts(0, 0, 0);
        }
        assertThat(service.importBook("valid.json", content(VALID)).status()).isEqualTo(BookImportStatus.IMPORTED);
    }

    @ParameterizedTest
    @MethodSource("invalidFilenames")
    void rejectsFilenamesThatCannotBeStored(String filename) {
        BookImportResultDTO result = service.importBook(filename, content(VALID));

        assertThat(result.status()).isEqualTo(BookImportStatus.REJECTED);
        assertThat(result.errors()).anySatisfy(error -> assertThat(error.path()).isEqualTo("$.sourceFilename"));
        assertCounts(0, 0, 0);
    }

    static Stream<String> invalidFilenames() {
        return Stream.of(null, "", " ", "f".repeat(256));
    }

    @Test
    void rollsBackAllRowsOnTransientFailureAndAllowsRetryingTheSameBook() {
        doThrow(new TransientDataAccessResourceException("Simulated database outage"))
                .when(options).saveAll(anyList());

        assertThatThrownBy(() -> service.importBook("cave.json", content(VALID)))
                .isInstanceOf(TransientDataAccessResourceException.class)
                .hasMessageContaining("Simulated database outage");
        assertCounts(0, 0, 0);
        reset(options);
        assertThat(service.importBook("cave.json", content(VALID)).status()).isEqualTo(BookImportStatus.IMPORTED);
        assertCounts(1, 3, 4);
    }

    @Test
    void rollsBackConstraintFailuresWithoutAffectingTheNextBook() {
        doThrow(new DataIntegrityViolationException("Simulated option constraint failure"))
                .when(options).saveAll(anyList());

        assertThatThrownBy(() -> service.importBook("cave.json", content(VALID)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("Simulated option constraint failure");
        assertCounts(0, 0, 0);
        reset(options);
        BookImportResultDTO next = service.importBook("forest.json", content(VALID.replace("The Cave", "The Forest")));
        assertThat(next.status()).isEqualTo(BookImportStatus.IMPORTED);
        assertCounts(1, 3, 4);
    }

    private void assertCounts(long bookCount, long sectionCount, long optionCount) {
        assertThat(books.count()).isEqualTo(bookCount);
        assertThat(sections.count()).isEqualTo(sectionCount);
        assertThat(options.count()).isEqualTo(optionCount);
    }

    private static byte[] content(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
