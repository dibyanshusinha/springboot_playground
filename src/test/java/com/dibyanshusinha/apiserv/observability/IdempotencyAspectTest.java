package com.dibyanshusinha.apiserv.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IdempotencyAspectTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private HttpServletRequest request;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private IdempotencyAspect aspect;

    // Dummy methods for reflection
    public ResponseEntity<String> dummyMethod() {
        return null;
    }

    public ResponseEntity<Void> dummyVoidMethod() {
        return null;
    }

    public String dummyNonResponseEntityMethod() {
        return null;
    }

    @SuppressWarnings("rawtypes")
    public ResponseEntity dummyRawMethod() {
        return null;
    }

    public ResponseEntity<?> dummyWildcardMethod() {
        return null;
    }

    @BeforeEach
    void setUp() {
        aspect = new IdempotencyAspect(jdbcTemplate, objectMapper);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void handleIdempotencyReturnsProceedWhenRequestContextIsNull() throws Throwable {
        RequestContextHolder.resetRequestAttributes();
        when(joinPoint.proceed()).thenReturn("success");

        Object result = aspect.handleIdempotency(joinPoint);

        assertThat(result).isEqualTo("success");
        verify(joinPoint).proceed();
    }

    @Test
    void handleIdempotencyReturnsProceedWhenKeyIsMissingOrBlank() throws Throwable {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(request.getHeader("Idempotency-Key")).thenReturn(null);
        when(joinPoint.proceed()).thenReturn("success");

        Object result = aspect.handleIdempotency(joinPoint);
        assertThat(result).isEqualTo("success");

        when(request.getHeader("Idempotency-Key")).thenReturn("   ");
        result = aspect.handleIdempotency(joinPoint);
        assertThat(result).isEqualTo("success");

        verify(joinPoint, times(2)).proceed();
    }

    @Test
    void handleIdempotencyReplaysResponseOnCacheHitForResponseEntity() throws Throwable {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(request.getHeader("Idempotency-Key")).thenReturn("test-key");

        // Mock database cache hit
        Map<String, Object> cachedMap = new HashMap<>();
        cachedMap.put("response_status", 200);
        cachedMap.put("response_body", "\"hello-world\"");
        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class))).thenReturn(cachedMap);

        // Mock method signature returning ResponseEntity<String>
        MethodSignature signature = mock(MethodSignature.class);
        Method method = IdempotencyAspectTest.class.getMethod("dummyMethod");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);

        Object result = aspect.handleIdempotency(joinPoint);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(response.getBody()).isEqualTo("hello-world");

        verify(joinPoint, never()).proceed();
    }

    @Test
    void handleIdempotencyReplaysResponseOnCacheHitWithNullOrBlankBody() throws Throwable {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(request.getHeader("Idempotency-Key")).thenReturn("test-key");

        // Case A: bodyJson is empty string
        Map<String, Object> cachedMap1 = new HashMap<>();
        cachedMap1.put("response_status", 204);
        cachedMap1.put("response_body", "");
        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class))).thenReturn(cachedMap1);

        MethodSignature signature = mock(MethodSignature.class);
        Method method = IdempotencyAspectTest.class.getMethod("dummyVoidMethod");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);

        Object result1 = aspect.handleIdempotency(joinPoint);
        assertThat(result1).isInstanceOf(ResponseEntity.class);
        assertThat(((ResponseEntity<?>) result1).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Case B: bodyJson is null
        Map<String, Object> cachedMap2 = new HashMap<>();
        cachedMap2.put("response_status", 204);
        cachedMap2.put("response_body", null);
        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class))).thenReturn(cachedMap2);

        Object result2 = aspect.handleIdempotency(joinPoint);
        assertThat(result2).isInstanceOf(ResponseEntity.class);
        assertThat(((ResponseEntity<?>) result2).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Case C: bodyJson is not empty/null, but return type is Void (makes !bodyType.equals(Void.class) false)
        Map<String, Object> cachedMap3 = new HashMap<>();
        cachedMap3.put("response_status", 204);
        cachedMap3.put("response_body", "{}");
        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class))).thenReturn(cachedMap3);

        Object result3 = aspect.handleIdempotency(joinPoint);
        assertThat(result3).isInstanceOf(ResponseEntity.class);
        assertThat(((ResponseEntity<?>) result3).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        verify(joinPoint, never()).proceed();
    }

    @Test
    void handleIdempotencyReturnsProceedOnCacheHitWhenReturnTypeIsNotResponseEntity() throws Throwable {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(request.getHeader("Idempotency-Key")).thenReturn("test-key");

        Map<String, Object> cachedMap = new HashMap<>();
        cachedMap.put("response_status", 200);
        cachedMap.put("response_body", "raw-value");
        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class))).thenReturn(cachedMap);

        MethodSignature signature = mock(MethodSignature.class);
        Method method = IdempotencyAspectTest.class.getMethod("dummyNonResponseEntityMethod");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);

        when(joinPoint.proceed()).thenReturn("fallback-value");

        Object result = aspect.handleIdempotency(joinPoint);
        assertThat(result).isEqualTo("fallback-value");

        verify(joinPoint).proceed();
    }

    @Test
    void handleIdempotencyReturnsConflictResponseOnDuplicateKeyException() throws Throwable {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(request.getHeader("Idempotency-Key")).thenReturn("test-key");
        when(request.getRequestURI()).thenReturn("/v1/products");

        // Cache miss
        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class))).thenThrow(new EmptyResultDataAccessException(1));

        // Insert throws duplicate key exception
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        Object result = aspect.handleIdempotency(joinPoint);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(joinPoint, never()).proceed();
    }

    @Test
    void handleIdempotencyDeletesKeyAndRethrowsWhenProceedThrowsException() throws Throwable {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(request.getHeader("Idempotency-Key")).thenReturn("test-key");

        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class))).thenThrow(new EmptyResultDataAccessException(1));
        when(joinPoint.proceed()).thenThrow(new RuntimeException("business exception"));

        assertThatThrownBy(() -> aspect.handleIdempotency(joinPoint))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("business exception");

        // Verify key is deleted
        verify(jdbcTemplate).update(eq("DELETE FROM idempotency_keys WHERE key_value = :key"),
                argThat((MapSqlParameterSource p) -> "test-key".equals(p.getValue("key"))));
    }

    @Test
    void handleIdempotencyUpdatesKeyOnSuccessForResponseEntity() throws Throwable {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(request.getHeader("Idempotency-Key")).thenReturn("test-key");

        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class))).thenThrow(new EmptyResultDataAccessException(1));
        
        ResponseEntity<String> response = ResponseEntity.ok("my-response-body");
        when(joinPoint.proceed()).thenReturn(response);

        Object result = aspect.handleIdempotency(joinPoint);
        assertThat(result).isEqualTo(response);

        // Verify insert and then update is called
        verify(jdbcTemplate).update(contains("INSERT INTO idempotency_keys"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate).update(contains("UPDATE idempotency_keys SET response_status = :status, response_body = :body"), any(MapSqlParameterSource.class));
    }

    @Test
    void handleIdempotencyUpdatesKeyWith200OnSuccessForNonResponseEntity() throws Throwable {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(request.getHeader("Idempotency-Key")).thenReturn("test-key");

        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class))).thenThrow(new EmptyResultDataAccessException(1));
        when(joinPoint.proceed()).thenReturn("plain-string");

        Object result = aspect.handleIdempotency(joinPoint);
        assertThat(result).isEqualTo("plain-string");

        verify(jdbcTemplate).update(contains("INSERT INTO idempotency_keys"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate).update(contains("UPDATE idempotency_keys SET response_status = :status, response_body = :body"), 
                argThat((MapSqlParameterSource p) -> 
                        p.getValue("status").equals(200) && p.getValue("body") == null
                ));
    }

    @Test
    @SuppressWarnings("rawtypes")
    void handleIdempotencyReplaysResponseOnCacheHitForRawResponseEntity() throws Throwable {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(request.getHeader("Idempotency-Key")).thenReturn("test-key");

        Map<String, Object> cachedMap = new HashMap<>();
        cachedMap.put("response_status", 200);
        cachedMap.put("response_body", "\"raw-body\"");
        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class))).thenReturn(cachedMap);

        MethodSignature signature = mock(MethodSignature.class);
        Method method = IdempotencyAspectTest.class.getMethod("dummyRawMethod");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);

        Object result = aspect.handleIdempotency(joinPoint);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("raw-body");
    }

    @Test
    void handleIdempotencyReplaysResponseOnCacheHitForWildcardResponseEntity() throws Throwable {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(request.getHeader("Idempotency-Key")).thenReturn("test-key");

        Map<String, Object> cachedMap = new HashMap<>();
        cachedMap.put("response_status", 200);
        cachedMap.put("response_body", "\"wild-body\"");
        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class))).thenReturn(cachedMap);

        MethodSignature signature = mock(MethodSignature.class);
        Method method = IdempotencyAspectTest.class.getMethod("dummyWildcardMethod");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);

        Object result = aspect.handleIdempotency(joinPoint);

        assertThat(result).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("wild-body");
    }

    @Test
    void handleIdempotencyUpdatesKeyOnSuccessForResponseEntityWithNullBody() throws Throwable {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(request.getHeader("Idempotency-Key")).thenReturn("test-key");

        when(jdbcTemplate.queryForMap(anyString(), any(SqlParameterSource.class))).thenThrow(new EmptyResultDataAccessException(1));
        
        ResponseEntity<Void> response = ResponseEntity.noContent().build();
        when(joinPoint.proceed()).thenReturn(response);

        Object result = aspect.handleIdempotency(joinPoint);
        assertThat(result).isEqualTo(response);

        verify(jdbcTemplate).update(contains("INSERT INTO idempotency_keys"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate).update(contains("UPDATE idempotency_keys SET response_status = :status, response_body = :body"), 
                argThat((MapSqlParameterSource p) -> 
                        p.getValue("status").equals(204) && p.getValue("body") == null
                ));
    }
}
