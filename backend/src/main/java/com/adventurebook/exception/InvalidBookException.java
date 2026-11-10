package com.adventurebook.exception;

import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;

@Getter
public class InvalidBookException extends RuntimeException {

    private final List<BookValidationError> errors;

    public InvalidBookException(List<BookValidationError> errors) {
        super(errors.stream().map(error -> error.path() + ": " + error.message())
                .collect(Collectors.joining("; ")));
        this.errors = List.copyOf(errors);
    }
}
