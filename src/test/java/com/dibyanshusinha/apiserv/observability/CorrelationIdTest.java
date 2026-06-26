package com.dibyanshusinha.apiserv.observability;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class CorrelationIdTest {

    @Test
    void resolveReturnsTrimmedWhenValid() {
        String valid = "  my-correlation-id-123  ";
        assertThat(CorrelationId.resolve(valid)).isEqualTo("my-correlation-id-123");
    }

    @Test
    void resolveGeneratesNewUuidWhenInvalid() {
        String invalid = "bad correlation ID";
        String resolved = CorrelationId.resolve(invalid);
        assertThat(resolved).isNotBlank();
        assertThat(CorrelationId.isValid(resolved)).isTrue();
    }

    @Test
    void isValidReturnsFalseOnNull() {
        assertThat(CorrelationId.isValid(null)).isFalse();
    }

    @Test
    void isValidReturnsFalseOnBlank() {
        assertThat(CorrelationId.isValid("")).isFalse();
        assertThat(CorrelationId.isValid("   ")).isFalse();
    }

    @Test
    void isValidReturnsFalseWhenTooLong() {
        String longId = "a".repeat(129);
        assertThat(CorrelationId.isValid(longId)).isFalse();
    }

    @Test
    void isValidReturnsFalseOnInvalidCharacters() {
        assertThat(CorrelationId.isValid("id with spaces")).isFalse();
        assertThat(CorrelationId.isValid("id/with/slashes")).isFalse();
        assertThat(CorrelationId.isValid("id@symbol")).isFalse();
    }

    @Test
    void isValidReturnsTrueOnValidStrings() {
        assertThat(CorrelationId.isValid("valid-id_123.abc:XYZ")).isTrue();
    }

    @Test
    void privateConstructorTest() throws Exception {
        Constructor<CorrelationId> constructor = CorrelationId.class.getDeclaredConstructor();
        assertThat(constructor.canAccess(null)).isFalse();
        constructor.setAccessible(true);
        CorrelationId instance = constructor.newInstance();
        assertThat(instance).isNotNull();
    }
}
