package com.dibyanshusinha.apiserv.e2e;

import com.dibyanshusinha.apiserv.e2e.testsupport.E2EDatabase;
import com.dibyanshusinha.apiserv.e2e.testsupport.E2EScenario;
import com.dibyanshusinha.apiserv.e2e.testsupport.E2EScenarioReportExtension;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("E2ETests > API > Product > v1")
@ExtendWith(E2EScenarioReportExtension.class)
class ProductApiE2ETest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String API_PATH = "/v1/products";
    private static final String API_PATH_WITH_ID = API_PATH + "/{id}";
    private static final String API_PATH_WITH_TRAILING_SLASH = API_PATH + "/";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String baseUrl = System.getProperty("e2e.base-url", System.getenv().getOrDefault("E2E_BASE_URL", "http://localhost:8080"));
    private final E2EDatabase database = E2EDatabase.fromConfiguration();

    @AfterEach
    void cleanUpE2EProducts() {
        database.deleteProductsBySkuPrefix("E2E-");
    }

    @Test
    @DisplayName("Product lifecycle workflow")
    @E2EScenario(service = "Product", method = "POST", route = API_PATH,
            workflow = "create product as part of product lifecycle", response = "201 Created")
    @E2EScenario(service = "Product", method = "GET", route = API_PATH_WITH_ID,
            workflow = "get created product as part of product lifecycle", response = "200 OK")
    @E2EScenario(service = "Product", method = "GET", route = API_PATH,
            workflow = "list products after create as part of product lifecycle", response = "200 OK")
    @E2EScenario(service = "Product", method = "PATCH", route = API_PATH_WITH_ID,
            workflow = "update product as part of product lifecycle", response = "200 OK")
    @E2EScenario(service = "Product", method = "DELETE", route = API_PATH_WITH_ID,
            workflow = "delete product as part of product lifecycle", response = "204 No Content")
    void productLifecycleWorkflow() throws Exception {
        String sku = "E2E-" + System.currentTimeMillis();

        JsonNode created = postProduct(sku);
        long id = created.path("id").asLong();
        assertThat(created.path("sku").asText()).isEqualTo(sku);
        assertProductRowCount(sku, 1);

        HttpResponse<String> getResponse = sendJson("GET", API_PATH_WITH_TRAILING_SLASH + id, null);
        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertThat(OBJECT_MAPPER.readTree(getResponse.body()).path("id").asLong()).isEqualTo(id);

        HttpResponse<String> listResponse = sendJson("GET", API_PATH + "?page=0&size=20&sortBy=createdAt&direction=DESC", null);
        assertThat(listResponse.statusCode()).isEqualTo(200);
        assertThat(listResponse.body()).contains(sku);

        HttpResponse<String> updateResponse = sendJson("PATCH", API_PATH_WITH_TRAILING_SLASH + id, """
                {
                  "name": "E2E Updated Product",
                  "sku": "%s",
                  "description": "Updated by E2E workflow",
                  "price": 79.99,
                  "active": true
                }
                """.formatted(sku));
        assertThat(updateResponse.statusCode()).isEqualTo(200);
        assertThat(new BigDecimal(OBJECT_MAPPER.readTree(updateResponse.body()).path("price").asText())).isEqualByComparingTo("79.99");

        HttpResponse<String> deleteResponse = sendJson("DELETE", API_PATH_WITH_TRAILING_SLASH + id, null);
        assertThat(deleteResponse.statusCode()).isEqualTo(204);

        HttpResponse<String> deletedGetResponse = sendJson("GET", API_PATH_WITH_TRAILING_SLASH + id, null);
        assertThat(deletedGetResponse.statusCode()).isEqualTo(404);
        assertProductRowCount(sku, 0);
    }

    @Test
    @DisplayName("Invalid product request")
    @E2EScenario(service = "Product", method = "POST", route = API_PATH,
            workflow = "reject invalid product request", response = "400 Bad Request")
    void invalidProductRequestReturnsBadRequest() throws Exception {
        HttpResponse<String> response = sendJson("POST", API_PATH, """
                {
                  "name": "",
                  "sku": "",
                  "price": -1
                }
                """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Validation failed");
    }

    @Test
    @DisplayName("Duplicate SKU request")
    @E2EScenario(service = "Product", method = "POST", route = API_PATH,
            workflow = "reject duplicate SKU", response = "409 Conflict")
    void duplicateSkuReturnsConflict() throws Exception {
        String sku = "E2E-DUP-" + System.currentTimeMillis();
        postProduct(sku);
        assertProductRowCount(sku, 1);

        HttpResponse<String> response = sendJson("POST", API_PATH, productPayload(sku));

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("Duplicate SKU");
        assertProductRowCount(sku, 1);
    }

    private JsonNode postProduct(String sku) throws IOException, InterruptedException {
        HttpResponse<String> response = sendJson("POST", API_PATH, productPayload(sku));
        assertThat(response.statusCode()).isEqualTo(201);
        return OBJECT_MAPPER.readTree(response.body());
    }

    private String productPayload(String sku) {
        return """
                {
                  "name": "E2E Product",
                  "sku": "%s",
                  "description": "Created by E2E workflow",
                  "price": 49.99,
                  "active": true
                }
                """.formatted(sku);
    }

    private HttpResponse<String> sendJson(String method, String path, String body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json");

        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertProductRowCount(String sku, long expectedCount) throws Exception {
        if (database.isAvailable()) {
            assertThat(database.countProductsBySku(sku)).isEqualTo(expectedCount);
        }
    }
}
