package com.adventurebook.repository;

import java.time.Instant;

import com.adventurebook.enums.Difficulty;
import com.adventurebook.enums.SectionType;
import com.adventurebook.model.Book;
import com.adventurebook.model.BookOption;
import com.adventurebook.model.BookSection;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.url=jdbc:h2:mem:book-persistence;DB_CLOSE_DELAY=-1")
@Transactional
class BookRepositoryTests {

    @Autowired
    private BookRepository books;

    @Autowired
    private BookSectionRepository sections;

    @Autowired
    private BookOptionRepository options;

    @Autowired
    private EntityManager entityManager;

    @Test
    void loadsOptionsByStoredPositionRatherThanInsertionOrder() {
        Book book = books.saveAndFlush(book("3".repeat(64)));
        BookSection beginning = saveSection(book, "begin", "Choose a path.", SectionType.BEGIN);
        BookSection ending = saveSection(book, "end", "The adventure ends.", SectionType.END);
        options.saveAndFlush(option(beginning, 1, "First inserted", ending));
        options.saveAndFlush(option(beginning, 0, "Second inserted", ending));
        Long sectionId = beginning.getId();
        entityManager.clear();

        assertThat(options.findAllBySectionIdOrderByPositionAsc(sectionId))
                .extracting(BookOption::getDescription).containsExactly("Second inserted", "First inserted");
    }

    @Test
    void rejectsDuplicateContentHashesInTheDatabase() {
        books.saveAndFlush(book("b".repeat(64)));

        assertThatThrownBy(() -> books.saveAndFlush(book("b".repeat(64))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("UK_BOOK_CONTENT_HASH");
    }

    @Test
    void rejectsDuplicateSectionIdentifiersWithinOneBook() {
        Book book = books.saveAndFlush(book("c".repeat(64)));
        saveSection(book, "same-key", "Beginning", SectionType.BEGIN);

        assertThatThrownBy(() -> saveSection(book, "same-key", "Ending", SectionType.END))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("UK_BOOK_SECTION_KEY");
    }

    @Test
    void rejectsDuplicateOptionPositionsWithinOneSection() {
        Book book = books.saveAndFlush(book("1".repeat(64)));
        BookSection beginning = saveSection(book, "begin", "Beginning", SectionType.BEGIN);
        BookSection ending = saveSection(book, "end", "Ending", SectionType.END);
        options.saveAndFlush(option(beginning, 0, "First choice", ending));

        assertThatThrownBy(() -> options.saveAndFlush(option(beginning, 0, "Duplicate position", ending)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("UK_SECTION_OPTION_POSITION");
    }

    @Test
    void preservesStoredImportTimeWhenReloading() {
        Instant originalImportTime = Instant.parse("2020-01-01T00:00:00Z");
        Book book = Book.builder().title("An Earlier Adventure").author("Alex Rivers")
                .difficulty(Difficulty.EASY).sourceFilename("earlier.json")
                .contentHash("2".repeat(64)).importedAt(originalImportTime).build();
        Long id = books.saveAndFlush(book).getId();
        entityManager.clear();

        assertThat(books.findById(id).orElseThrow().getImportedAt()).isEqualTo(originalImportTime);
    }

    private static Book book(String hash) {
        return Book.builder().title("The Hidden Cave").author("Alex Rivers")
                .difficulty(Difficulty.MEDIUM).sourceFilename("hidden-cave.json").contentHash(hash).build();
    }

    private BookSection saveSection(Book book, String key, String text, SectionType type) {
        return sections.saveAndFlush(BookSection.builder()
                .book(book).sectionKey(key).text(text).type(type).build());
    }

    private static BookOption option(BookSection section, int position, String description,
                                     BookSection destination) {
        return BookOption.builder().section(section).position(position).description(description)
                .destination(destination).build();
    }
}
