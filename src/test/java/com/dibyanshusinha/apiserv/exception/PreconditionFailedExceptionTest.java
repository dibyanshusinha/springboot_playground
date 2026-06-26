package com.dibyanshusinha.apiserv.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class PreconditionFailedExceptionTest {

    @Test
    void constructorSetsMessageAndHttpStatus() {
        PreconditionFailedException exception = new PreconditionFailedException("test message");
        assertThat(exception.getMessage()).isEqualTo("test message");
        assertThat(exception.getStatus()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(exception.getTitle()).isEqualTo("Precondition Failed");
    }
}
