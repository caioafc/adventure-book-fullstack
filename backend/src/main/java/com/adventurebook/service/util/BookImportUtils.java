package com.adventurebook.service.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import com.adventurebook.dto.BookImportDTO;
import com.adventurebook.enums.SectionType;
import com.adventurebook.exception.BookValidationError;
import tools.jackson.core.JacksonException;

public final class BookImportUtils {

    private BookImportUtils() {
    }

    public static String contentHash(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available in the Java runtime", exception);
        }
    }

    public static String jsonPath(JacksonException exception) {
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

    public static List<BookValidationError> validateGraph(List<BookImportDTO.Section> sections) {
        List<BookValidationError> errors = new ArrayList<>();
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
        return List.copyOf(errors);
    }
}
