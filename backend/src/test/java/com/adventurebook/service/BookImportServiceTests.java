package com.adventurebook.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.adventurebook.dto.BookImportResultDTO;
import com.adventurebook.enums.BookImportStatus;
import com.adventurebook.model.BookSection;
import com.adventurebook.repository.BookOptionRepository;
import com.adventurebook.repository.BookSectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.url=jdbc:h2:mem:book-import-validation;DB_CLOSE_DELAY=-1")
@Transactional
class BookImportServiceTests {

    private static final String OPTION = """
            {"description":"Open door","gotoId":500,
             "consequence":{"type":"LOSE_HEALTH","value":"3","text":"A scratch"}}
            """.strip();
    private static final String VALID = """
            {"title":"The Escape","author":"Alex","difficulty":"HARD","type":"ignored",
             "sections":[
               {"id":1,"text":"Start","type":"BEGIN","options":[%s]},
               {"id":"500","text":"Free","type":"END"}]}
            """.formatted(OPTION);

    @Autowired
    private BookImportService service;

    @Autowired
    private BookSectionRepository sections;

    @Autowired
    private BookOptionRepository options;

    @Test
    void normalizesNumericAndStringIdsAndParsesStringConsequenceValues() {
        List<BookSection> book = importSections(VALID);

        assertThat(book).extracting(BookSection::getSectionKey).containsExactly("1", "500");
        assertThat(options.findAllBySectionIdOrderByPositionAsc(book.getFirst().getId()))
                .singleElement().satisfies(option -> {
                    assertThat(option.getDestination().getId()).isEqualTo(book.getLast().getId());
                    assertThat(option.getConsequenceValue()).isEqualTo(3);
                });
        assertThat(options.findAllBySectionIdOrderByPositionAsc(book.getLast().getId())).isEmpty();
    }

    @Test
    void preservesNumericIdsLargerThanTheLongRange() {
        String largeId = "999999999999999999999999999999";
        List<BookSection> book = importSections(VALID.replace("\"id\":1", "\"id\":" + largeId));

        assertThat(book.getFirst().getSectionKey()).isEqualTo(largeId);
    }

    @Test
    void acceptsSectionTextLongerThanVarcharLimits() {
        String story = "The adventure continues. ".repeat(1000);

        assertThat(importSections(VALID.replace("Start", story)).getFirst().getText()).isEqualTo(story);
    }

    @Test
    void acceptsCyclesMultipleEndingsUnreachableSectionsAndOptionalConsequences() {
        List<BookSection> book = importSections("""
                {"title":"Paths","author":"Alex","difficulty":"EASY","extra":"ignored",
                 "sections":[
                   {"id":"1","text":"Start","type":"BEGIN","extra":42,"options":[
                     {"description":"Loop","gotoId":1,"extra":true},
                     {"description":"Leave","gotoId":"2"}]},
                   {"id":2,"text":"Done","type":"END","options":[]},
                   {"id":3,"text":"Another ending","type":"END"},
                   {"id":4,"text":"Unreachable","type":"NODE","options":[
                     {"description":"Stay","gotoId":4}]}]}
                """);

        assertThat(book).hasSize(4);
        assertThat(options.findAllBySectionIdOrderByPositionAsc(book.getFirst().getId()).getFirst()
                .getConsequenceType()).isNull();
    }

    @Test
    void acceptsNumericConsequenceValuesAndHealing() {
        List<BookSection> book = importSections(VALID.replace("\"value\":\"3\"", "\"value\":2")
                .replace("LOSE_HEALTH", "GAIN_HEALTH"));

        assertThat(options.findAllBySectionIdOrderByPositionAsc(book.getFirst().getId()).getFirst()
                .getConsequenceValue()).isEqualTo(2);
    }

    @ParameterizedTest
    @MethodSource("invalidBooks")
    void rejectsInvalidMetadataAndGraphRules(String json, String expectedError) {
        assertRejected(json, expectedError);
    }

    static Stream<Arguments> invalidBooks() {
        return Stream.of(
                Arguments.of(VALID.replace("The Escape", " "), "$.title"),
                Arguments.of(VALID.replace("Alex", "a".repeat(256)), "$.author"),
                Arguments.of(VALID.replace("The Escape", "t".repeat(513)), "$.title"),
                Arguments.of(VALID.replace("Start", " "), ".text"),
                Arguments.of(VALID.replace("HARD", "IMPOSSIBLE"), "difficulty"),
                Arguments.of(VALID.replace("\"type\":\"BEGIN\"", "\"type\":\"UNKNOWN\""), ".type"),
                Arguments.of(VALID.replace("LOSE_HEALTH", "TELEPORT"), ".consequence.type"),
                Arguments.of(VALID.replace("\"difficulty\":\"HARD\"", "\"difficulty\":0"), "difficulty"),
                Arguments.of(VALID.replace("\"type\":\"BEGIN\"", "\"type\":\"NODE\""), "exactly one beginning"),
                Arguments.of(VALID.replace("\"type\":\"END\"", "\"type\":\"BEGIN\""), "found 2"),
                Arguments.of(VALID.replace("\"type\":\"END\"", "\"type\":\"NODE\""), "at least one ending"),
                Arguments.of(VALID.replace("\"id\":\"500\"", "\"id\":\"1\""), "Duplicate section identifier"),
                Arguments.of(VALID.replace("\"gotoId\":500", "\"gotoId\":999"), "Destination section does not exist: 999"),
                Arguments.of(VALID.replace("[" + OPTION + "]", "[]"), "Non-ending section"),
                Arguments.of(VALID.replace("[" + OPTION + "]", "[null]"), "$.sections[0].options[0]"),
                Arguments.of(VALID.replace("Open door", "d".repeat(256)), ".description"),
                Arguments.of(VALID.replace("A scratch", "c".repeat(256)), ".consequence.text"),
                Arguments.of(VALID.replace("\"id\":1", "\"id\":null"), ".id"),
                Arguments.of(VALID.replace("\"id\":1", "\"id\":\" \""), ".id"),
                Arguments.of("{}", "$.title"),
                Arguments.of("null", "JSON object"),
                Arguments.of(VALID + " {}", "Trailing token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "null", "1.5", "\"1.5\"", "\"invalid\"", "2147483648", "true", "{}"})
    void rejectsInvalidConsequenceAmountsWithoutTruncatingOrOverflowing(String amount) {
        assertRejected(VALID.replace("\"value\":\"3\"", "\"value\":" + amount), "value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"forest\"", "\"1/2\"", "-1", "1.5", "\"1.5\"", "1e2", "true", "{}", "[]"})
    void rejectsIdsAndDestinationsThatAreNotNonNegativeIntegers(String id) {
        assertRejected(VALID.replace("\"id\":1", "\"id\":" + id), ".id");
        assertRejected(VALID.replace("\"gotoId\":500", "\"gotoId\":" + id), ".gotoId");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "{broken", "[]"})
    void rejectsEmptyMalformedOrNonObjectJson(String json) {
        assertThat(importBook(json).status()).isEqualTo(BookImportStatus.REJECTED);
    }

    @ParameterizedTest
    @CsvSource({
            "dragon-quest.json, Book JSON is empty",
            "the-prisoner.json, Non-ending section",
            "crystal-caverns.json, Non-ending section",
            "pirates-jade-sea.json, Destination section does not exist: 999",
            "pirates-jade-sea.json, Non-ending section"
    })
    void reportsErrorsFromInvalidTestBooks(String filename, String expectedError) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/books/" + filename)) {
            assertThat(input).as("Test resource %s", filename).isNotNull();
            byte[] content = input.readAllBytes();
            assertRejected(service.importBook(filename, content), expectedError);
        }
    }

    @Test
    void accumulatesIndependentSemanticErrors() {
        String invalid = VALID.replace("The Escape", " ")
                .replace("\"gotoId\":500", "\"gotoId\":999");

        BookImportResultDTO result = importBook(invalid);
        assertThat(result.status()).isEqualTo(BookImportStatus.REJECTED);
        assertThat(result.errors()).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("$.title"));
        assertThat(result.errors()).anySatisfy(issue ->
                assertThat(issue.path()).isEqualTo("$.sections[0].options[0].gotoId"));
    }

    private List<BookSection> importSections(String json) {
        assertThat(importBook(json).status()).isEqualTo(BookImportStatus.IMPORTED);
        return sections.findAll().stream().sorted(Comparator.comparing(BookSection::getId)).toList();
    }

    private BookImportResultDTO importBook(String json) {
        return service.importBook("validation.json", json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertRejected(String json, String expectedError) {
        assertRejected(importBook(json), expectedError);
    }

    private void assertRejected(BookImportResultDTO result, String expectedError) {
        assertThat(result.status()).isEqualTo(BookImportStatus.REJECTED);
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error.path() + ": " + error.message()).contains(expectedError));
    }
}
