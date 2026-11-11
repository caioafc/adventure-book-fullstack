package com.adventurebook.controller;

import com.adventurebook.dto.ApiErrorDTO;
import com.adventurebook.enums.ApiErrorCode;
import com.adventurebook.exception.ApiException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorDTO> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(toResponse(exception.getErrorCode()));
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception exception, @Nullable Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ApiErrorDTO response = switch (exception) {
            case MethodArgumentTypeMismatchException mismatch -> invalidParameter(mismatch.getName());
            case HandlerMethodValidationException validation
                    when !validation.isForReturnValue() && !validation.getParameterValidationResults().isEmpty() ->
                invalidParameter(validation.getParameterValidationResults().getFirst()
                        .getMethodParameter().getParameterName());
            default -> httpError(status);
        };
        return super.handleExceptionInternal(exception, response, headers, status, request);
    }

    private ApiErrorDTO invalidParameter(@Nullable String name) {
        ApiErrorCode error = switch (name) {
            case "bookId" -> ApiErrorCode.INVALID_BOOK_ID;
            case "sectionId" -> ApiErrorCode.INVALID_SECTION_ID;
            case "difficulty" -> ApiErrorCode.INVALID_DIFFICULTY;
            case null, default -> ApiErrorCode.INVALID_REQUEST;
        };
        return toResponse(error);
    }

    private ApiErrorDTO httpError(HttpStatusCode status) {
        HttpStatus knownStatus = HttpStatus.resolve(status.value());
        return knownStatus == null
                ? new ApiErrorDTO("HTTP_" + status.value(), "The request could not be completed.")
                : new ApiErrorDTO(knownStatus.name(), knownStatus.getReasonPhrase() + ".");
    }

    private ApiErrorDTO toResponse(ApiErrorCode error) {
        return new ApiErrorDTO(error.name(), error.getDescription());
    }
}
