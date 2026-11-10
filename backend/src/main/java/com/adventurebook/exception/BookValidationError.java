package com.adventurebook.exception;

public record BookValidationError(String path, String message) {
}
