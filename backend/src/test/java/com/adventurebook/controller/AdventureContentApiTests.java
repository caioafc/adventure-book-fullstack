package com.adventurebook.controller;

import java.nio.charset.StandardCharsets;

import com.adventurebook.enums.BookImportStatus;
import com.adventurebook.service.BookImportService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:book-adventure;DB_CLOSE_DELAY=-1")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdventureContentApiTests {

    private static final String STORY = """
            {
              "title": "The Hill Gate",
              "author": "Mira Vale",
              "difficulty": "EASY",
              "sections": [
                {
                  "id": 10, "text": "You reach a gate.", "type": "BEGIN",
                  "options": [
                    {"description": "Climb", "gotoId": "20", "consequence": {
                      "type": "LOSE_HEALTH", "value": "3", "text": "You scrape your knee."
                    }},
                    {"description": "Leave", "gotoId": "42"},
                    {"description": "Follow the path", "gotoId": 50, "consequence": {
                      "type": "LOSE_HEALTH", "value": 12, "text": "A rockslide hits you."
                    }}
                  ]
                },
                {
                  "id": 20, "text": "You find a bench.", "type": "NODE",
                  "options": [
                    {"description": "Rest and return", "gotoId": "10", "consequence": {
                      "type": "GAIN_HEALTH", "value": 2, "text": "You feel better."
                    }}
                  ]
                },
                {"id": 42, "text": "You walk home.", "type": "END"},
                {"id": "50", "text": "You find a garden.", "type": "END"}
              ]
            }
            """;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private BookImportService importer;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mvc;
    private long bookId;
    private long otherBookId;
    private long beginningId;
    private long loopId;
    private long endingId;
    private long gardenId;
    private long legacyBeginningId;

    @BeforeAll
    void importAdventures() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        bookId = importBook(STORY);
        otherBookId = importBook(STORY.replace("The Hill Gate", "The Hill Bridge"));
        beginningId = sectionId(bookId, "10");
        loopId = sectionId(bookId, "20");
        endingId = sectionId(bookId, "42");
        gardenId = sectionId(bookId, "50");
        legacyBeginningId = sectionId(otherBookId, "10");
        jdbc.update("update book_sections set section_key = ? where id = ?", "old/start", legacyBeginningId);
    }

    @Test
    void returnsBookDetailsAndBeginningIdentifier() throws Exception {
        mvc.perform(get("/api/books/{bookId}", bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookId))
                .andExpect(jsonPath("$.title").value("The Hill Gate"))
                .andExpect(jsonPath("$.author").value("Mira Vale"))
                .andExpect(jsonPath("$.difficulty").value("EASY"))
                .andExpect(jsonPath("$.beginningSectionId").value(beginningId));
    }

    @Test
    void returnsChoicesInOrderWithNumericDestinationsAndOptionalConsequences() throws Exception {
        mvc.perform(get("/api/books/{bookId}/sections/{sectionId}", bookId, beginningId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(beginningId))
                .andExpect(jsonPath("$.text").value("You reach a gate."))
                .andExpect(jsonPath("$.type").value("BEGIN"))
                .andExpect(jsonPath("$.options", hasSize(3)))
                .andExpect(jsonPath("$.options[0].description").value("Climb"))
                .andExpect(jsonPath("$.options[0].gotoId").value(loopId))
                .andExpect(jsonPath("$.options[0].consequence.type").value("LOSE_HEALTH"))
                .andExpect(jsonPath("$.options[0].consequence.value").value(3))
                .andExpect(jsonPath("$.options[0].consequence.text").value("You scrape your knee."))
                .andExpect(jsonPath("$.options[1].gotoId").value(endingId))
                .andExpect(jsonPath("$.options[1].consequence").value(nullValue()))
                .andExpect(jsonPath("$.options[2].gotoId").value(gardenId))
                .andExpect(jsonPath("$.options[2].consequence.type").value("LOSE_HEALTH"))
                .andExpect(jsonPath("$.options[2].consequence.value").value(12))
                .andExpect(jsonPath("$.options[2].consequence.text").value("A rockslide hits you."));
    }

    @Test
    void exposesCyclesWithoutChangingSectionContent() throws Exception {
        String opening = mvc.perform(get("/api/books/{bookId}/sections/{sectionId}", bookId, beginningId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/books/{bookId}/sections/{sectionId}", bookId, loopId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].gotoId").value(beginningId))
                .andExpect(jsonPath("$.options[0].consequence.type").value("GAIN_HEALTH"))
                .andExpect(jsonPath("$.options[0].consequence.value").value(2))
                .andExpect(jsonPath("$.options[0].consequence.text").value("You feel better."));

        mvc.perform(get("/api/books/{bookId}/sections/{sectionId}", bookId, beginningId))
                .andExpect(status().isOk())
                .andExpect(content().string(opening));
    }

    @Test
    void returnsEndingsWithNoChoices() throws Exception {
        mvc.perform(get("/api/books/{bookId}/sections/{sectionId}", bookId, endingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(endingId))
                .andExpect(jsonPath("$.type").value("END"))
                .andExpect(jsonPath("$.options", hasSize(0)));

        mvc.perform(get("/api/books/{bookId}/sections/{sectionId}", bookId, gardenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(gardenId))
                .andExpect(jsonPath("$.type").value("END"))
                .andExpect(jsonPath("$.options", hasSize(0)));
    }

    @Test
    void returnsNotFoundForMissingBooksAndBookScopedSections() throws Exception {
        mvc.perform(get("/api/books/{bookId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"))
                .andExpect(jsonPath("$.description").value("The requested book was not found."));
        mvc.perform(get("/api/books/{bookId}/sections/{sectionId}", Long.MAX_VALUE, beginningId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SECTION_NOT_FOUND"));
        mvc.perform(get("/api/books/{bookId}/sections/{sectionId}", otherBookId, loopId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SECTION_NOT_FOUND"))
                .andExpect(jsonPath("$.description").value("The requested section was not found in this book."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-id", "0", "-1"})
    void rejectsInvalidBookIds(String invalidId) throws Exception {
        mvc.perform(get("/api/books/{bookId}", invalidId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BOOK_ID"))
                .andExpect(jsonPath("$.description").value("Book ID must be a positive whole number."));
        mvc.perform(get("/api/books/{bookId}/sections/{sectionId}", invalidId, beginningId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BOOK_ID"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"forest", "0x1", "0", "-1", "1.5", "9223372036854775808"})
    void rejectsInvalidSectionIds(String invalidId) throws Exception {
        mvc.perform(get("/api/books/{bookId}/sections/{sectionId}", bookId, invalidId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SECTION_ID"))
                .andExpect(jsonPath("$.description")
                        .value("Section ID must be a positive whole number within the supported range."));
    }

    @Test
    void retrievesPreviouslyImportedSectionsWithNamedKeysByDatabaseId() throws Exception {
        mvc.perform(get("/api/books/{bookId}", otherBookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beginningSectionId").value(legacyBeginningId));
        mvc.perform(get("/api/books/{bookId}/sections/{sectionId}", otherBookId, legacyBeginningId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("You reach a gate."));
    }

    private long sectionId(long id, String key) {
        return jdbc.queryForObject("select id from book_sections where book_id = ? and section_key = ?",
                Long.class, id, key);
    }

    private long importBook(String story) {
        var result = importer.importBook("test-adventure.json", story.getBytes(StandardCharsets.UTF_8));
        assertThat(result.status()).as("Import errors: %s", result.errors()).isEqualTo(BookImportStatus.IMPORTED);
        return result.bookId();
    }
}
