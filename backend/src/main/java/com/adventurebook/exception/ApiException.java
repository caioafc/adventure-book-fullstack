package com.adventurebook.exception;

import com.adventurebook.enums.ApiErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

    private final ApiErrorCode errorCode;
    private final HttpStatus status;

    public ApiException(ApiErrorCode errorCode, HttpStatus status) {
        super(errorCode.getDescription());
        this.errorCode = errorCode;
        this.status = status;
    }
}
