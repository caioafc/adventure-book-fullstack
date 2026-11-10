package com.adventurebook.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import com.adventurebook.dto.BookImportDTO;
import com.adventurebook.exception.InvalidBookException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.url=jdbc:h2:mem:book-import-validation;DB_CLOSE_DELAY=-1")
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

    @Test
    void normalizesNumericAndStringIdsAndParsesStringConsequenceValues() {
        BookImportDTO book = parse(VALID);

        assertThat(book.sections().getFirst().id()).isEqualTo("1");
        assertThat(book.sections().get(1).id()).isEqualTo("500");
        assertThat(book.sections().getFirst().options().getFirst().gotoId()).isEqualTo("500");
        assertThat(book.sections().getFirst().options().getFirst().consequence().value()).isEqualTo(3);
        assertThat(book.sections().get(1).options()).isNull();
    }

    @Test
    void acceptsArbitraryStringIdsWithoutNumericFormatting() {
        BookImportDTO book = parse(VALID.replace("\"id\":1", "\"id\":\"entrance-alpha\"")
                .replace("\"id\":\"500\"", "\"id\":\"escape-door\"")
                .replace("\"gotoId\":500", "\"gotoId\":\"escape-door\""));

        assertThat(book.sections().getFirst().id()).isEqualTo("entrance-alpha");
        assertThat(book.sections().getFirst().options().getFirst().gotoId()).isEqualTo("escape-door");
    }

    @Test
    void preservesNumericIdsLargerThanTheLongRange() {
        String largeId = "999999999999999999999999999999";
        BookImportDTO book = parse(VALID.replace("\"id\":1", "\"id\":" + largeId));

        assertThat(book.sections().getFirst().id()).isEqualTo(largeId);
    }

    @Test
    void acceptsSectionTextLongerThanVarcharLimits() {
        String story = "The adventure continues. ".repeat(1000);

        assertThat(parse(VALID.replace("Start", story)).sections().getFirst().text()).isEqualTo(story);
    }

    @Test
    void acceptsCyclesMultipleEndingsUnreachableSectionsAndOptionalConsequences() {
        BookImportDTO book = parse("""
                {"title":"Paths","author":"Alex","difficulty":"EASY","extra":"ignored",
                 "sections":[
                   {"id":"start","text":"Start","type":"BEGIN","extra":42,"options":[
                     {"description":"Loop","gotoId":"start","extra":true},
                     {"description":"Leave","gotoId":"end"}]},
                   {"id":"end","text":"Done","type":"END","options":[]},
                   {"id":"other-end","text":"Another ending","type":"END"},
                   {"id":"island","text":"Unreachable","type":"NODE","options":[
                     {"description":"Stay","gotoId":"island"}]}]}
                """);

        assertThat(book.sections()).hasSize(4);
        assertThat(book.sections().getFirst().options().getFirst().consequence()).isNull();
    }

    @Test
    void acceptsNumericConsequenceValuesAndHealing() {
        BookImportDTO book = parse(VALID.replace("\"value\":\"3\"", "\"value\":2")
                .replace("LOSE_HEALTH", "GAIN_HEALTH"));

        assertThat(book.sections().getFirst().options().getFirst().consequence().value()).isEqualTo(2);
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
    @ValueSource(strings = {"true", "{}", "[]"})
    void rejectsNonStringNonNumericIds(String id) {
        assertRejected(VALID.replace("\"id\":1", "\"id\":" + id), "identifiers");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "{broken", "[]"})
    void rejectsEmptyMalformedOrNonObjectJson(String json) {
        assertThatThrownBy(() -> parse(json)).isInstanceOf(InvalidBookException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "dragon-quest.json, Book JSON is empty",
            "the-prisoner.json, Non-ending section",
            "crystal-caverns.json, Non-ending section",
            "pirates-jade-sea.json, Destination section does not exist: 999",
            "pirates-jade-sea.json, Non-ending section"
    })
    void reportsErrorsFromEverySuppliedFixture(String filename, String expectedError) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/books/" + filename)) {
            assertThat(input).as("Supplied fixture %s", filename).isNotNull();
            byte[] content = input.readAllBytes();
            assertThatThrownBy(() -> service.parse(content)).isInstanceOf(InvalidBookException.class)
                    .hasMessageContaining(expectedError);
        }
    }

    @Test
    void accumulatesIndependentSemanticErrors() {
        String invalid = VALID.replace("The Escape", " ")
                .replace("\"gotoId\":500", "\"gotoId\":999");

        assertThatThrownBy(() -> parse(invalid)).isInstanceOfSatisfying(InvalidBookException.class, error -> {
            assertThat(error.getErrors()).anySatisfy(issue -> assertThat(issue.path()).isEqualTo("$.title"));
            assertThat(error.getErrors()).anySatisfy(issue ->
                    assertThat(issue.path()).isEqualTo("$.sections[0].options[0].gotoId"));
        });
    }

    private BookImportDTO parse(String json) {
        return service.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertRejected(String json, String expectedError) {
        assertThatThrownBy(() -> parse(json)).isInstanceOf(InvalidBookException.class)
                .hasMessageContaining(expectedError);
    }
}
