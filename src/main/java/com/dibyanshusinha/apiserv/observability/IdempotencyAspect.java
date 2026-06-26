package com.dibyanshusinha.apiserv.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

@Aspect
@Component
public class IdempotencyAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyAspect.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyAspect(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(com.dibyanshusinha.apiserv.observability.Idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String key = request.getHeader("Idempotency-Key");

        if (key == null || key.isBlank()) {
            return joinPoint.proceed();
        }

        // 1. Check if the key exists
        Map<String, Object> cached = fetchKey(key);
        if (cached != null) {
            log.info("Idempotency key hit: replaying cached response for key [{}]", key);
            int status = (Integer) cached.get("response_status");
            String bodyJson = (String) cached.get("response_body");

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Class<?> returnType = method.getReturnType();

            if (ResponseEntity.class.isAssignableFrom(returnType)) {
                Class<?> bodyType = Object.class;
                java.lang.reflect.Type genericReturnType = method.getGenericReturnType();
                if (genericReturnType instanceof java.lang.reflect.ParameterizedType paramType) {
                    java.lang.reflect.Type[] actualTypeArguments = paramType.getActualTypeArguments();
                    if (actualTypeArguments[0] instanceof Class<?> clazz) {
                        bodyType = clazz;
                    }
                }

                Object body = null;
                if (bodyJson != null && !bodyJson.isBlank() && !bodyType.equals(Void.class)) {
                    body = objectMapper.readValue(bodyJson, bodyType);
                }
                return ResponseEntity.status(status)
                        .header("Idempotent-Replayed", "true")
                        .body(body);
            }

            return joinPoint.proceed();
        }

        // 2. Insert key in-progress lock
        try {
            insertKey(key);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Idempotency conflict: key [{}] already exists or is in progress", key);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new com.dibyanshusinha.apiserv.generated.model.ErrorResponse(
                            "Idempotency Conflict",
                            HttpStatus.CONFLICT.value(),
                            "A request with the same idempotency key is already in progress or has completed.",
                            request.getRequestURI(),
                            CorrelationId.resolve(request.getHeader(CorrelationId.HEADER_NAME)))
                    );
        }

        // 3. Proceed with execution
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable throwable) {
            deleteKey(key);
            throw throwable;
        }

        // 4. Update key with final response
        if (result instanceof ResponseEntity<?> responseEntity) {
            int status = responseEntity.getStatusCode().value();
            Object body = responseEntity.getBody();
            String bodyJson = (body != null) ? objectMapper.writeValueAsString(body) : null;
            updateKey(key, status, bodyJson);
        } else {
            updateKey(key, 200, null);
        }

        return result;
    }

    private Map<String, Object> fetchKey(String key) {
        String sql = "SELECT response_status, response_body FROM idempotency_keys WHERE key_value = :key";
        try {
            return jdbcTemplate.queryForMap(sql, Map.of("key", key));
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    private void insertKey(String key) {
        String sql = "INSERT INTO idempotency_keys (key_value, response_status, response_body, created_at) " +
                     "VALUES (:key, 201, NULL, :now)";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("key", key)
                .addValue("now", Timestamp.from(Instant.now()));
        jdbcTemplate.update(sql, params);
    }

    private void updateKey(String key, int status, String bodyJson) {
        String sql = "UPDATE idempotency_keys SET response_status = :status, response_body = :body WHERE key_value = :key";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("key", key)
                .addValue("status", status)
                .addValue("body", bodyJson);
        jdbcTemplate.update(sql, params);
    }

    private void deleteKey(String key) {
        String sql = "DELETE FROM idempotency_keys WHERE key_value = :key";
        jdbcTemplate.update(sql, Map.of("key", key));
    }
}
