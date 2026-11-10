package com.adventurebook.dto;

import java.util.List;

import com.adventurebook.exception.BookValidationError;

public record BookImportFailureDTO(
        String sourceFilename,
        String archivedFilename,
        List<BookValidationError> errors) {
}
