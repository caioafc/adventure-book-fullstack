package com.adventurebook.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApiErrorCode {
    INVALID_BOOK_ID("Book ID must be a positive whole number."),
    INVALID_SECTION_ID("Section ID must be a positive whole number within the supported range."),
    INVALID_DIFFICULTY("Difficulty must be EASY, MEDIUM, or HARD."),
    INVALID_REQUEST("One or more request parameters are invalid."),
    BOOK_NOT_FOUND("The requested book was not found."),
    BEGINNING_SECTION_NOT_FOUND("This book has no beginning section."),
    SECTION_NOT_FOUND("The requested section was not found in this book.");

    private final String description;
}
