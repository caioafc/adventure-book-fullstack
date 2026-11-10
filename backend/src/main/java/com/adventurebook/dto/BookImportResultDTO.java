package com.adventurebook.dto;

import java.util.List;

import com.adventurebook.enums.BookImportStatus;
import com.adventurebook.exception.BookValidationError;

public record BookImportResultDTO(
        BookImportStatus status,
        Long bookId,
        String contentHash,
        List<BookValidationError> errors) {
}
