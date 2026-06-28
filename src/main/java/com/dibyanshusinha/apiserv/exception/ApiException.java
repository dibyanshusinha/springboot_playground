package com.dibyanshusinha.apiserv.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;

@Getter
public class ApiException extends RuntimeException {

    private final @NonNull HttpStatus status;
    private final String title;

    protected ApiException(@NonNull HttpStatus status, String title, String message) {
        super(message);
        this.status = status;
        this.title = title;
    }
}
