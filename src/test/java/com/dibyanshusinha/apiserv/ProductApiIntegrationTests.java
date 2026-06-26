package com.dibyanshusinha.apiserv;

import com.dibyanshusinha.apiserv.observability.CorrelationId;
import com.dibyanshusinha.apiserv.service.products.repository.ProductRepository;
import com.dibyanshusinha.apiserv.testsupport.ApiScenario;
import com.dibyanshusinha.apiserv.testsupport.ApiScenarioCoverageExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static com.dibyanshusinha.apiserv.service.products.util.ProductConstants.API_PATH;
import static com.dibyanshusinha.apiserv.service.products.util.ProductConstants.API_PATH_WITH_ID;
import static com.dibyanshusinha.apiserv.service.products.util.ProductConstants.API_PATH_WITH_TRAILING_SLASH;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dibyanshusinha.apiserv.testsupport.TestcontainersConfig;
import org.springframework.context.annotation.Import;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("IntegrationTests > Product > Routes")
@ExtendWith(ApiScenarioCoverageExtension.class)
@Import(TestcontainersConfig.class)
class ProductApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /v1/products > Request valid product > Response 201 Created with product body")
    @ApiScenario(service = "Product", method = "POST", route = API_PATH,
            request = "valid product", response = "201 Created with product body")
    void createProductReturnsCreatedProduct() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Mechanical Keyboard",
                                  "sku": "KEY-001",
                                  "description": "Compact keyboard with tactile switches",
                                  "price": 129.99,
                                  "active": true
                                }
                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(API_PATH_WITH_TRAILING_SLASH + "\\d+")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.sku").value("KEY-001"))
                .andExpect(jsonPath("$.price").value(129.99))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("POST /v1/products > Request valid product without active flag > Response 201 Created with active=true")
    @ApiScenario(service = "Product", method = "POST", route = API_PATH,
            request = "valid product without active flag", response = "201 Created with active=true")
    void createProductDefaultsActiveToTrueWhenOmitted() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Notebook Stand",
                                  "sku": "STD-DEFAULT-001",
                                  "description": "Adjustable aluminum stand",
                                  "price": 89.99
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("GET /v1/products > Request page sorted by name ASC > Response 200 OK with paged products")
    @ApiScenario(service = "Product", method = "GET", route = API_PATH,
            request = "page sorted by name ASC", response = "200 OK with paged products")
    void listProductsReturnsPagedProducts() throws Exception {
        createProduct("USB-C Hub", "HUB-001", "49.99");
        createProduct("Laptop Stand", "STD-001", "39.99");

        String token = java.util.Base64.getEncoder().encodeToString("page:0".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(get(API_PATH)
                        .param("pageToken", token)
                        .param("pageSize", "10")
                        .param("sortBy", "name")
                        .param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Laptop Stand"))
                .andExpect(jsonPath("$.content[1].name").value("USB-C Hub"));
    }

    @Test
    @DisplayName("GET /v1/products/{id} > Request existing product id > Response 200 OK with product body")
    @ApiScenario(service = "Product", method = "GET", route = API_PATH_WITH_ID,
            request = "existing product id", response = "200 OK with product body")
    void getProductReturnsProductById() throws Exception {
        long id = createProduct("Wireless Mouse", "MOU-001", "25.50");

        mockMvc.perform(get(API_PATH_WITH_ID, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Wireless Mouse"));
    }

    @Test
    @DisplayName("PATCH /v1/products/{id} > Request valid update > Response 200 OK with updated product body")
    @ApiScenario(service = "Product", method = "PATCH", route = API_PATH_WITH_ID,
            request = "valid update", response = "200 OK with updated product body")
    void updateProductReturnsUpdatedProduct() throws Exception {
        long id = createProduct("Monitor", "MON-001", "199.99");

        mockMvc.perform(patch(API_PATH_WITH_ID, id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "4K Monitor",
                                  "sku": "MON-4K-001",
                                  "description": "Updated display",
                                  "price": 299.99,
                                  "active": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("4K Monitor"))
                .andExpect(jsonPath("$.sku").value("MON-4K-001"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("PATCH /v1/products/{id} > Request missing product id > Response 404 Product not found")
    @ApiScenario(service = "Product", method = "PATCH", route = API_PATH_WITH_ID,
            request = "missing product id", response = "404 Product not found")
    void updateProductRejectsMissingProduct() throws Exception {
        mockMvc.perform(patch(API_PATH_WITH_ID, 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "4K Monitor",
                                  "sku": "MON-MISSING-001",
                                  "description": "Updated display",
                                  "price": 299.99,
                                  "active": true
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"));
    }

    @Test
    @DisplayName("DELETE /v1/products/{id} > Request existing product id > Response 204 No Content and subsequent 404")
    @ApiScenario(service = "Product", method = "DELETE", route = API_PATH_WITH_ID,
            request = "existing product id", response = "204 No Content and subsequent 404")
    void deleteProductRemovesProduct() throws Exception {
        long id = createProduct("Desk Lamp", "LMP-001", "19.99");

        mockMvc.perform(delete(API_PATH_WITH_ID, id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(API_PATH_WITH_ID, id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Product not found"));
    }

    @Test
    @DisplayName("POST /v1/products > Request invalid validation fields > Response 400 Validation failed")
    @ApiScenario(service = "Product", method = "POST", route = API_PATH,
            request = "invalid validation fields", response = "400 Validation failed")
    void createProductRejectsInvalidPayload() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "sku": "",
                                  "price": -1
                                }
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.sku").exists())
                .andExpect(jsonPath("$.errors.price").exists());
    }

    @Test
    @DisplayName("POST /v1/products > Request malformed JSON > Response 400 Malformed request")
    @ApiScenario(service = "Product", method = "POST", route = API_PATH,
            request = "malformed JSON", response = "400 Malformed request")
    void createProductRejectsMalformedJson() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Broken Product",
                                  "sku": "BROKEN-001",
                                  "price":
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    @DisplayName("POST /v1/products > Request duplicate SKU > Response 409 Duplicate SKU")
    @ApiScenario(service = "Product", method = "POST", route = API_PATH,
            request = "duplicate SKU", response = "409 Duplicate SKU")
    void createProductRejectsDuplicateSku() throws Exception {
        createProduct("Docking Station", "DOC-001", "149.99");

        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Another Dock",
                                  "sku": "DOC-001",
                                  "price": 159.99
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate SKU"));
    }

    @Test
    @DisplayName("PATCH /v1/products/{id} > Request duplicate SKU > Response 409 Duplicate SKU")
    @ApiScenario(service = "Product", method = "PATCH", route = API_PATH_WITH_ID,
            request = "duplicate SKU", response = "409 Duplicate SKU")
    void updateProductRejectsDuplicateSku() throws Exception {
        long id = createProduct("Docking Station", "DOC-UPDATE-001", "149.99");
        createProduct("Wireless Charger", "CHG-UPDATE-001", "29.99");

        mockMvc.perform(patch(API_PATH_WITH_ID, id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated Dock",
                                  "sku": "CHG-UPDATE-001",
                                  "description": "Conflicting SKU",
                                  "price": 159.99,
                                  "active": true
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate SKU"));
    }

    @Test
    @DisplayName("GET /v1/products > Request unsupported sort field > Response 400 Invalid sort property")
    @ApiScenario(service = "Product", method = "GET", route = API_PATH,
            request = "unsupported sort field", response = "400 Invalid sort property")
    void listProductsRejectsUnsupportedSortProperty() throws Exception {
        mockMvc.perform(get(API_PATH)
                        .param("sortBy", "unknownField"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid sort property"));
    }

    @Test
    @DisplayName("GET /v1/products > Request size above maximum > Response 400 Invalid request parameter")
    @ApiScenario(service = "Product", method = "GET", route = API_PATH,
            request = "size above maximum", response = "400 Invalid request parameter")
    void listProductsRejectsInvalidPageSize() throws Exception {
        mockMvc.perform(get(API_PATH)
                        .param("pageSize", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request parameter"));
    }

    @Test
    @DisplayName("GET /v1/products > Request with correlation id > Response echoes correlation id")
    void requestWithCorrelationIdEchoesHeader() throws Exception {
        mockMvc.perform(get(API_PATH)
                        .header(CorrelationId.HEADER_NAME, "gateway-correlation-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationId.HEADER_NAME, "gateway-correlation-123"));
    }

    @Test
    @DisplayName("GET /v1/products > Request without correlation id > Response generates correlation id")
    void requestWithoutCorrelationIdGeneratesHeaderAndErrorProperty() throws Exception {
        mockMvc.perform(get(API_PATH)
                        .param("sortBy", "unknownField"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(CorrelationId.HEADER_NAME, matchesPattern("[A-Za-z0-9._:-]+")))
                .andExpect(jsonPath("$.correlationId", matchesPattern("[A-Za-z0-9._:-]+")));
    }

    @Test
    @DisplayName("PATCH /v1/products/{id} > Request with mismatched If-Match > Response 412 Precondition Failed")
    void updateProductWithMismatchedIfMatchReturns412() throws Exception {
        long id = createProduct("Monitor", "MON-IF-001", "199.99");
        mockMvc.perform(patch(API_PATH_WITH_ID, id)
                        .header("If-Match", "\"99\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "New Monitor",
                                  "price": 249.99
                                }
                                """))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    @DisplayName("PATCH /v1/products/{id} > Request with valid If-Match > Response 200 OK with ETag")
    void updateProductWithValidIfMatchReturns200AndETag() throws Exception {
        long id = createProduct("Monitor", "MON-IF-002", "199.99");
        mockMvc.perform(patch(API_PATH_WITH_ID, id)
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "New Monitor",
                                  "price": 249.99
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""));
    }

    @Test
    @DisplayName("POST /v1/products > Re-submit request with same Idempotency-Key > Response replayed with header")
    void createProductIdempotencyReplaysCachedResponse() throws Exception {
        String key = java.util.UUID.randomUUID().toString();
        String payload = """
                {
                  "name": "Idempotent Keyboard",
                  "sku": "KEY-IDEMP-001",
                  "price": 59.99
                }
                """;

        mockMvc.perform(post(API_PATH)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));

        mockMvc.perform(post(API_PATH)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));
    }

    private long createProduct(String name, String sku, String price) throws Exception {
        String response = mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "sku": "%s",
                                  "description": "Test product",
                                  "price": %s,
                                  "active": true
                                }
                                """.formatted(name, sku, price)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return Long.parseLong(response.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }
}
