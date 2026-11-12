package com.adventurebook.controller;

import java.util.UUID;

import com.adventurebook.enums.Difficulty;
import com.adventurebook.model.Book;
import com.adventurebook.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:book-catalogue;DB_CLOSE_DELAY=-1")
@Transactional
class BookCatalogueApiTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private BookRepository books;

    private MockMvc mvc;
    private Long firstBookId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        firstBookId = saveBook("Zulu Cave", "Mira Vale", Difficulty.EASY).getId();
        saveBook("Alpha Tower", "Owen Pike", Difficulty.HARD);
        saveBook("Moon Cave", "Mira Vale", Difficulty.HARD);
        saveBook("Silent Bay", "Leila North", Difficulty.EASY);
    }

    @Test
    void returnsSummariesAndDefaultPaginationWithoutPersistenceDetails() throws Exception {
        mvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(4)))
                .andExpect(jsonPath("$.content[0].id").value(firstBookId))
                .andExpect(jsonPath("$.content[0].title").value("Zulu Cave"))
                .andExpect(jsonPath("$.content[0].author").value("Mira Vale"))
                .andExpect(jsonPath("$.content[0].difficulty").value("EASY"))
                .andExpect(jsonPath("$.content[0].contentHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].sourceFilename").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @ParameterizedTest
    @CsvSource({"0,Zulu Cave,Alpha Tower,false", "1,Moon Cave,Silent Bay,true"})
    void paginatesInBookIdOrder(int page, String first, String second, boolean last) throws Exception {
        mvc.perform(get("/api/books").param("page", String.valueOf(page)).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].title").value(first))
                .andExpect(jsonPath("$.content[1].title").value(second))
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(last));
    }

    @ParameterizedTest
    @CsvSource({"cAvE,2,Zulu Cave", "pIkE,1,Alpha Tower"})
    void searchesTitleAndAuthorIgnoringCase(String search, int count, String first) throws Exception {
        mvc.perform(get("/api/books").param("search", search))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(count))
                .andExpect(jsonPath("$.content[0].title").value(first));
    }

    @Test
    void combinesSearchAndDifficultyAndPaginatesMatches() throws Exception {
        mvc.perform(get("/api/books").param("search", " CAVE ")
                        .param("difficulty", "HARD").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Moon Cave"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void paginatesFilteredResultsUsingTheFilteredCount() throws Exception {
        mvc.perform(get("/api/books").param("search", "MIRA VALE").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Moon Cave"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void filtersByDifficultyWithoutOtherFilters() throws Exception {
        mvc.perform(get("/api/books").param("difficulty", "HARD").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Alpha Tower"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void treatsBlankFiltersAsAbsent() throws Exception {
        mvc.perform(get("/api/books").param("search", " ").param("difficulty", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void returnsAnEmptyPageWhenFiltersDoNotMatch() throws Exception {
        mvc.perform(get("/api/books").param("search", "Tower").param("difficulty", "EASY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void returnsAnEmptyPageBeyondTheLastPage() throws Exception {
        mvc.perform(get("/api/books").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void keepsFixedOrderingRegardlessOfRequestedSort() throws Exception {
        mvc.perform(get("/api/books").param("sort", "notAProperty,desc").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(firstBookId));
    }

    @Test
    void handlesAnEmptyCatalogue() throws Exception {
        books.deleteAll();

        mvc.perform(get("/api/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void advertisesAllDifficultyOptionsWithAnEmptyCatalogue() throws Exception {
        books.deleteAll();

        mvc.perform(get("/api/books/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].key").value("difficulty"))
                .andExpect(jsonPath("$[0].label").value("Difficulty"))
                .andExpect(jsonPath("$[0].type").value("select"))
                .andExpect(jsonPath("$[0].options", contains("EASY", "MEDIUM", "HARD")));
    }

    @Test
    void rejectsUnknownDifficultyValues() throws Exception {
        mvc.perform(get("/api/books").param("difficulty", "IMPOSSIBLE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DIFFICULTY"))
                .andExpect(jsonPath("$.description").value("Difficulty must be EASY, MEDIUM, or HARD."));
    }

    @Test
    void returnsAnErrorBodyForAnUnknownRoute() throws Exception {
        mvc.perform(get("/api/books/1/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.description").value("Not Found."));
    }

    private Book saveBook(String title, String author, Difficulty difficulty) {
        return books.saveAndFlush(Book.builder().title(title).author(author).difficulty(difficulty)
                .sourceFilename("catalogue-test.json").contentHash(UUID.randomUUID().toString()).build());
    }
}
