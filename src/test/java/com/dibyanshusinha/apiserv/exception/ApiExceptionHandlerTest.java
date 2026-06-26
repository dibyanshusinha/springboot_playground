package com.dibyanshusinha.apiserv.exception;

import com.dibyanshusinha.apiserv.generated.model.ErrorResponse;
import com.dibyanshusinha.apiserv.observability.CorrelationId;
import com.dibyanshusinha.apiserv.service.products.exception.ProductException;
import com.dibyanshusinha.apiserv.service.products.util.ProductConstants;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", ProductConstants.API_PATH);

    ApiExceptionHandlerTest() {
        request.setAttribute(CorrelationId.REQUEST_ATTRIBUTE, "test-correlation-id");
    }

    @Test
    void handleProductNotFoundReturnsNotFoundProblem() {
        ResponseEntity<ErrorResponse> problem = handler.handleApiException(ProductException.notFound(42L), request);

        assertProblem(problem, 404, "Product not found", "Product not found with id: 42");
    }

    @Test
    void handleDuplicateSkuReturnsConflictProblem() {
        ResponseEntity<ErrorResponse> problem = handler.handleApiException(ProductException.duplicateSku("SKU-1"), request);

        assertProblem(problem, 409, "Duplicate SKU", "Product SKU already exists: SKU-1");
    }

    @Test
    void handleInvalidSortPropertyReturnsBadRequestProblem() {
        ResponseEntity<ErrorResponse> problem = handler.handleApiException(ProductException.invalidSortProperty("bad"), request);

        assertProblem(problem, 400, "Invalid sort property", "Unsupported product sort property: bad");
    }

    @Test
    void handleValidationReturnsFieldErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "product");
        bindingResult.addError(new FieldError("product", "name", "Product name is required"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ErrorResponse> problem = handler.handleValidation(exception, request);

        assertProblem(problem, 400, "Validation failed", "One or more request fields are invalid.");
        assertThat(problem.getBody().getErrors())
                .containsEntry("name", "Product name is required");
    }

    @Test
    void handleConstraintViolationReturnsBadRequestProblem() {
        ResponseEntity<ErrorResponse> problem = handler.handleConstraintViolation(new ConstraintViolationException("bad parameter", java.util.Set.of()), request);

        assertProblem(problem, 400, "Invalid request parameter", "bad parameter");
    }

    @Test
    void handleUnreadableMessageReturnsMalformedRequestProblem() {
        ResponseEntity<ErrorResponse> problem = handler.handleUnreadableMessage(new HttpMessageNotReadableException("bad json"), request);

        assertProblem(problem, 400, "Malformed request", "Request body is missing or invalid.");
    }

    @Test
    void handleDataIntegrityReturnsConflictProblem() {
        ResponseEntity<ErrorResponse> problem = handler.handleDataIntegrity(new DataIntegrityViolationException("constraint"), request);

        assertProblem(problem, 409, "Data integrity violation", "Request conflicts with existing data.");
    }

    @Test
    void handleUnexpectedReturnsInternalServerErrorProblem() {
        ResponseEntity<ErrorResponse> problem = handler.handleUnexpected(new RuntimeException("boom"), request);

        assertProblem(problem, 500, "Unexpected error", "An unexpected error occurred.");
    }

    @Test
    void handleOptimisticLockingReturnsConflictProblem() {
        ResponseEntity<ErrorResponse> problem = handler.handleOptimisticLocking(new org.springframework.dao.OptimisticLockingFailureException("concurrent update"), request);

        assertProblem(problem, 409, "Concurrent modification", "The resource was modified by another request. Please retry.");
    }

    @Test
    void handlePreconditionFailedReturnsPreconditionFailedProblem() {
        ResponseEntity<ErrorResponse> problem = handler.handlePreconditionFailed(new PreconditionFailedException("etag mismatch"), request);

        assertProblem(problem, 412, "Precondition Failed", "etag mismatch");
    }

    @Test
    void handleUnexpectedResolvesCorrelationIdFromHeaderWhenAttributeIsMissing() {
        MockHttpServletRequest headerRequest = new MockHttpServletRequest("POST", ProductConstants.API_PATH);
        headerRequest.addHeader(CorrelationId.HEADER_NAME, "header-correlation-id");

        ResponseEntity<ErrorResponse> problem = handler.handleUnexpected(new RuntimeException("boom"), headerRequest);

        assertThat(problem.getBody().getCorrelationId()).isEqualTo("header-correlation-id");
    }

    @Test
    void handleUnexpectedResolvesCorrelationIdFromHeaderWhenAttributeIsNonString() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", ProductConstants.API_PATH);
        mockRequest.setAttribute(CorrelationId.REQUEST_ATTRIBUTE, 12345); // Non-string
        mockRequest.addHeader(CorrelationId.HEADER_NAME, "header-correlation-id");

        ResponseEntity<ErrorResponse> problem = handler.handleUnexpected(new RuntimeException("boom"), mockRequest);

        assertThat(problem.getBody().getCorrelationId()).isEqualTo("header-correlation-id");
    }

    @Test
    void handleUnexpectedResolvesCorrelationIdFromHeaderWhenAttributeIsBlankString() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest("POST", ProductConstants.API_PATH);
        mockRequest.setAttribute(CorrelationId.REQUEST_ATTRIBUTE, "   "); // Blank string
        mockRequest.addHeader(CorrelationId.HEADER_NAME, "header-correlation-id");

        ResponseEntity<ErrorResponse> problem = handler.handleUnexpected(new RuntimeException("boom"), mockRequest);

        assertThat(problem.getBody().getCorrelationId()).isEqualTo("header-correlation-id");
    }

    private void assertProblem(ResponseEntity<ErrorResponse> problem, int status, String title, String detail) {
        assertThat(problem.getStatusCode().value()).isEqualTo(status);
        assertThat(problem.getBody()).isNotNull();
        assertThat(problem.getBody().getStatus()).isEqualTo(status);
        assertThat(problem.getBody().getTitle()).isEqualTo(title);
        assertThat(problem.getBody().getDetail()).isEqualTo(detail);
        assertThat(problem.getBody().getType()).hasToString("https://api.example.com/problems/" + title.toLowerCase().replace(" ", "-"));
        assertThat(problem.getBody().getPath()).isEqualTo(ProductConstants.API_PATH);
        assertThat(problem.getBody().getCorrelationId()).isEqualTo("test-correlation-id");
    }
}
