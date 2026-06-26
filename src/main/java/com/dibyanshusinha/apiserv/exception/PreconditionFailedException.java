package com.dibyanshusinha.apiserv.exception;

import org.springframework.http.HttpStatus;

public class PreconditionFailedException extends ApiException {

    public PreconditionFailedException(String message) {
        super(HttpStatus.PRECONDITION_FAILED, "Precondition Failed", message);
    }
}
