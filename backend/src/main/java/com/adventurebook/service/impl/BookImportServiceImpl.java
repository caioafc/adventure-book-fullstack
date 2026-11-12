package com.adventurebook.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.adventurebook.dto.BookImportDTO;
import com.adventurebook.dto.BookImportResultDTO;
import com.adventurebook.enums.BookImportStatus;
import com.adventurebook.exception.BookValidationError;
import com.adventurebook.exception.InvalidBookException;
import com.adventurebook.model.Book;
import com.adventurebook.model.BookOption;
import com.adventurebook.model.BookSection;
import com.adventurebook.repository.BookOptionRepository;
import com.adventurebook.repository.BookRepository;
import com.adventurebook.repository.BookSectionRepository;
import com.adventurebook.service.BookImportService;
import com.adventurebook.service.util.BookImportUtils;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

import static com.adventurebook.service.util.BookImportUtils.contentHash;
import static com.adventurebook.service.util.BookImportUtils.jsonPath;
import static com.adventurebook.service.util.BookImportUtils.validateGraph;

@Service
@RequiredArgsConstructor
public class BookImportServiceImpl implements BookImportService {

    private final JsonMapper mapper;
    private final Validator validator;
    private final BookRepository books;
    private final BookSectionRepository sections;
    private final BookOptionRepository options;

    @Override
    @Transactional
    public BookImportResultDTO importBook(String sourceFilename, byte[] content) {
        String hash = content == null ? null : contentHash(content);

        if (sourceFilename == null || sourceFilename.isBlank() || sourceFilename.length() > 255) {
            return new BookImportResultDTO(BookImportStatus.REJECTED, null, hash,
                    List.of(new BookValidationError("$.sourceFilename",
                            "Source filename must be non-blank and at most 255 characters")));
        }

        try {
            return books.findByContentHash(hash)
                    .map(BookImportUtils::duplicate)
                    .orElseGet(() -> persist(parse(content), sourceFilename, hash));
        } catch (InvalidBookException exception) {
            return new BookImportResultDTO(BookImportStatus.REJECTED, null, hash, exception.getErrors());
        }
    }

    private BookImportResultDTO persist(BookImportDTO source, String filename, String hash) {
        Book book = saveBook(source, filename, hash);
        Map<String, BookSection> sectionsById = saveSections(source, book);
        saveOptions(source, sectionsById);
        return new BookImportResultDTO(BookImportStatus.IMPORTED, book.getId(), hash, List.of());
    }

    private Book saveBook(BookImportDTO source, String filename, String hash) {
        return books.save(Book.builder()
                .title(source.title())
                .author(source.author())
                .difficulty(source.difficulty())
                .sourceFilename(filename)
                .contentHash(hash).build());
    }

    private Map<String, BookSection> saveSections(BookImportDTO source, Book book) {
        Map<String, BookSection> sectionsById = new HashMap<>();
        for (BookImportDTO.Section sourceSection : source.sections()) {
            BookSection section = sections.save(BookSection.builder()
                    .book(book)
                    .sectionKey(sourceSection.id())
                    .text(sourceSection.text())
                    .type(sourceSection.type()).build());

            sectionsById.put(sourceSection.id(), section);
        }
        return sectionsById;
    }

    private void saveOptions(BookImportDTO source, Map<String, BookSection> sectionsById) {
        List<BookOption> bookOptions = new ArrayList<>();
        for (BookImportDTO.Section sourceSection : source.sections()) {
            if (sourceSection.options() == null) {
                continue;
            }

            for (int position = 0; position < sourceSection.options().size(); position++) {
                BookImportDTO.Option sourceOption = sourceSection.options().get(position);
                BookImportDTO.Consequence consequence = sourceOption.consequence();
                bookOptions.add(BookOption.builder()
                        .section(sectionsById.get(sourceSection.id()))
                        .position(position)
                        .description(sourceOption.description())
                        .destination(sectionsById.get(sourceOption.gotoId()))
                        .consequenceType(consequence == null ? null : consequence.type())
                        .consequenceValue(consequence == null ? null : consequence.value())
                        .consequenceText(consequence == null ? null : consequence.text()).build());
            }
        }

        options.saveAll(bookOptions);
    }

    private BookImportDTO parse(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidBookException(List.of(new BookValidationError("$", "Book JSON is empty")));
        }
        BookImportDTO book;
        try {
            book = mapper.readerFor(BookImportDTO.class)
                    .with(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(content);
        } catch (JacksonException exception) {
            throw new InvalidBookException(List.of(new BookValidationError(
                    jsonPath(exception), exception.getOriginalMessage())));
        }
        List<BookValidationError> errors = validate(book);
        if (!errors.isEmpty()) {
            throw new InvalidBookException(errors);
        }
        return book;
    }

    private List<BookValidationError> validate(BookImportDTO book) {
        List<BookValidationError> errors = new ArrayList<>();
        if (book == null) {
            return List.of(new BookValidationError("$", "Book must be a JSON object"));
        }
        validator.validate(book).forEach(violation -> errors.add(new BookValidationError(
                "$." + violation.getPropertyPath(), violation.getMessage())));
        if (book.sections() != null) {
            errors.addAll(validateGraph(book.sections()));
        }
        return errors.stream().sorted(Comparator.comparing(BookValidationError::path)
                .thenComparing(BookValidationError::message)).toList();
    }

}
