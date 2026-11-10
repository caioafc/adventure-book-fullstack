package com.adventurebook.repository;

import java.time.Instant;
import java.util.List;

import com.adventurebook.enums.ConsequenceType;
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
    void roundTripsCompleteBookWithConsequencesAndCycles() {
        Book book = books.saveAndFlush(book("a".repeat(64)));
        BookSection beginning = saveSection(book, "arrival", "You arrive at a cave.", SectionType.BEGIN);
        BookSection cave = saveSection(book, "deep-cave", "It is dark inside.", SectionType.NODE);
        BookSection ending = saveSection(book, "escape", "You are free.", SectionType.END);
        options.saveAllAndFlush(List.of(
                BookOption.builder().section(beginning).position(0).description("Enter the cave")
                        .destination(cave).consequenceType(ConsequenceType.LOSE_HEALTH)
                        .consequenceValue(3).consequenceText("A sharp rock cuts you.").build(),
                option(beginning, 1, "Leave immediately", ending),
                BookOption.builder().section(cave).position(0).description("Rest, then escape")
                        .destination(ending).consequenceType(ConsequenceType.GAIN_HEALTH)
                        .consequenceValue(2).consequenceText("You feel better.").build(),
                option(cave, 1, "Return to the entrance", beginning)));
        Long bookId = book.getId();
        entityManager.clear();

        Book loaded = books.findById(bookId).orElseThrow();
        assertThat(loaded.getTitle()).isEqualTo("The Hidden Cave");
        assertThat(loaded.getAuthor()).isEqualTo("Alex Rivers");
        assertThat(loaded.getDifficulty()).isEqualTo(Difficulty.MEDIUM);
        assertThat(loaded.getSourceFilename()).isEqualTo("hidden-cave.json");
        assertThat(loaded.getContentHash()).isEqualTo("a".repeat(64));
        assertThat(loaded.getImportedAt()).isNotNull();
        assertThat(sections.findAllByBookId(bookId)).hasSize(3);

        BookSection loadedBeginning = sections.findByBookIdAndSectionKey(bookId, "arrival").orElseThrow();
        assertThat(loadedBeginning.getType()).isEqualTo(SectionType.BEGIN);
        assertThat(loadedBeginning.getText()).isEqualTo("You arrive at a cave.");
        assertThat(loadedBeginning.getBook().getId()).isEqualTo(bookId);
        List<BookOption> beginningOptions = options.findAllBySectionIdOrderByPositionAsc(loadedBeginning.getId());
        assertThat(beginningOptions).extracting(BookOption::getDescription)
                .containsExactly("Enter the cave", "Leave immediately");
        assertThat(beginningOptions).extracting(BookOption::getPosition).containsExactly(0, 1);
        BookOption firstOption = beginningOptions.getFirst();
        assertThat(firstOption.getDestination().getSectionKey()).isEqualTo("deep-cave");
        assertThat(firstOption.getConsequenceType()).isEqualTo(ConsequenceType.LOSE_HEALTH);
        assertThat(firstOption.getConsequenceValue()).isEqualTo(3);
        assertThat(firstOption.getConsequenceText()).isEqualTo("A sharp rock cuts you.");
        assertThat(beginningOptions.get(1).getConsequenceType()).isNull();
        assertThat(beginningOptions.get(1).getConsequenceValue()).isNull();
        assertThat(beginningOptions.get(1).getConsequenceText()).isNull();

        BookSection loadedCave = sections.findByBookIdAndSectionKey(bookId, "deep-cave").orElseThrow();
        List<BookOption> caveOptions = options.findAllBySectionIdOrderByPositionAsc(loadedCave.getId());
        assertThat(caveOptions.getFirst().getConsequenceType()).isEqualTo(ConsequenceType.GAIN_HEALTH);
        assertThat(caveOptions.get(1).getDestination().getSectionKey()).isEqualTo("arrival");
        BookSection loadedEnding = sections.findByBookIdAndSectionKey(bookId, "escape").orElseThrow();
        assertThat(options.findAllBySectionIdOrderByPositionAsc(loadedEnding.getId())).isEmpty();
    }

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
    void allowsTheSameSectionIdentifierInDifferentBooks() {
        Book firstBook = books.saveAndFlush(book("d".repeat(64)));
        Book secondBook = books.saveAndFlush(book("e".repeat(64)));
        saveSection(firstBook, "opening", "First book", SectionType.BEGIN);
        saveSection(secondBook, "opening", "Second book", SectionType.BEGIN);
        Long firstId = firstBook.getId();
        Long secondId = secondBook.getId();
        entityManager.clear();

        assertThat(sections.findByBookIdAndSectionKey(firstId, "opening"))
                .hasValueSatisfying(found -> assertThat(found.getText()).isEqualTo("First book"));
        assertThat(sections.findByBookIdAndSectionKey(secondId, "opening"))
                .hasValueSatisfying(found -> assertThat(found.getText()).isEqualTo("Second book"));
    }

    @Test
    void findsImportedBooksByContentHash() {
        books.saveAndFlush(book("f".repeat(64)));
        entityManager.clear();

        assertThat(books.existsByContentHash("f".repeat(64))).isTrue();
        assertThat(books.findByContentHash("f".repeat(64)))
                .hasValueSatisfying(found -> assertThat(found.getTitle()).isEqualTo("The Hidden Cave"));
        assertThat(books.existsByContentHash("0".repeat(64))).isFalse();
        assertThat(books.findByContentHash("0".repeat(64))).isEmpty();
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
