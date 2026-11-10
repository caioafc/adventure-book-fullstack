package com.adventurebook.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.adventurebook.dto.BookImportDTO;
import com.adventurebook.enums.SectionType;
import com.adventurebook.exception.InvalidBookException;
import com.adventurebook.service.BookImportService;
import com.adventurebook.exception.BookValidationError;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class BookImportServiceImpl implements BookImportService {

    private final JsonMapper mapper;
    private final Validator validator;

    @Override
    public BookImportDTO parse(byte[] content) {
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
            validateGraph(book.sections(), errors);
        }
        return errors.stream().sorted(Comparator.comparing(BookValidationError::path)
                .thenComparing(BookValidationError::message)).toList();
    }

    private void validateGraph(List<BookImportDTO.Section> sections, List<BookValidationError> errors) {
        Set<String> identifiers = new HashSet<>();
        int beginnings = 0;
        int endings = 0;
        for (int i = 0; i < sections.size(); i++) {
            BookImportDTO.Section section = sections.get(i);
            if (section == null) {
                continue;
            }
            if (section.id() != null && !identifiers.add(section.id())) {
                errors.add(new BookValidationError("$.sections[" + i + "].id",
                        "Duplicate section identifier: " + section.id()));
            }
            if (section.type() == SectionType.BEGIN) {
                beginnings++;
            }
            if (section.type() == SectionType.END) {
                endings++;
            } else if (section.type() != null && (section.options() == null || section.options().isEmpty())) {
                errors.add(new BookValidationError("$.sections[" + i + "].options",
                        "Non-ending section must have at least one option"));
            }
        }
        if (beginnings != 1) {
            errors.add(new BookValidationError("$.sections", "Book must have exactly one beginning; found " + beginnings));
        }
        if (endings == 0) {
            errors.add(new BookValidationError("$.sections", "Book must have at least one ending"));
        }
        for (int i = 0; i < sections.size(); i++) {
            BookImportDTO.Section section = sections.get(i);
            if (section == null || section.options() == null) {
                continue;
            }
            for (int j = 0; j < section.options().size(); j++) {
                BookImportDTO.Option option = section.options().get(j);
                if (option != null && option.gotoId() != null && !identifiers.contains(option.gotoId())) {
                    errors.add(new BookValidationError("$.sections[" + i + "].options[" + j + "].gotoId",
                            "Destination section does not exist: " + option.gotoId()));
                }
            }
        }
    }

    private String jsonPath(JacksonException exception) {
        StringBuilder path = new StringBuilder("$");
        for (JacksonException.Reference reference : exception.getPath()) {
            if (reference.getPropertyName() != null) {
                path.append('.').append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
