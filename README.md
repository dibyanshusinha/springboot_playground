# Spring Boot REST API

A production-minded REST API with a product catalog module as the first sample API. The project demonstrates an API-first Spring Boot architecture with validation, persistence, database migrations, centralized error handling, OpenAPI documentation, Actuator observability, Docker, and integration tests.

## Tech stack

- Java 17
- Spring Boot 3.3
- Spring Web
- Spring JDBC with `NamedParameterJdbcTemplate`
- Bean Validation
- PostgreSQL for local development, tests, and runtime profiles
- Flyway database migrations
- OpenAPI Generator
- Springdoc OpenAPI / Swagger UI
- Spring Boot Actuator
- JUnit 5 and MockMvc

## Architecture

```text
com.dibyanshusinha.apiserv
├── config                 -> shared application configuration
├── exception              -> shared/global API exception handling
├── generated              -> generated API interfaces and models from OpenAPI
├── observability          -> correlation ID filter and observability helpers
└── service
    └── products
        ├── controller     -> product HTTP resources and validation
        ├── entity         -> product persistence model
        ├── exception      -> product-domain exceptions
        ├── mapper         -> product entity/DTO mapping
        ├── repository     -> product SQL access with named JDBC parameters
        └── ProductService -> product business rules and transactions
```

## API-first contract

The OpenAPI contract is the source of truth:

```text
openapi/product-service/openapi.yaml
```

`openapi/product-service` is a git submodule backed by the independent Product API spec project. API designers can evolve that project separately, while this Spring Boot service consumes the pinned submodule revision to generate API interfaces and models. The contract is not duplicated under application resources; runtime docs are exposed by Springdoc from the application at `/v3/api-docs`.

The spec is split by responsibility:

```text
openapi/product-service
├── openapi.yaml            -> public OpenAPI entrypoint
├── paths
│   └── products            -> product path operations
└── components
    ├── headers             -> reusable response headers
    ├── parameters          -> shared and domain query/path/header parameters
    ├── responses           -> shared and domain responses
    └── schemas             -> shared and domain schemas
```

Clone this service with the API spec submodule:

```bash
git clone --recurse-submodules <service-repo-url>
```

Initialize submodules after a normal clone:

```bash
git submodule update --init --recursive
```

Update the Product API spec consumed by this service:

```bash
git submodule update --remote openapi/product-service
```

Run the shared API design standard against the Product API spec:

```bash
mvn validate -Papi-design
```

The API spec project runs `dibyanshusinha/api-design-standard` through Spectral from:

```text
openapi/product-service/standards/api-design-standard
```

During Maven builds, OpenAPI Generator creates:

```text
target/generated-sources/openapi
```

The product controller implements the generated API interface, and product request/response/page models are generated from the contract. Handwritten code owns behavior, persistence, and mapping only.

## API endpoints

Base path:

```text
/v1/products
```

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/v1/products` | Create a product |
| `GET` | `/v1/products` | List products with pagination and sorting |
| `GET` | `/v1/products/{id}` | Get a product by id |
| `PUT` | `/v1/products/{id}` | Update a product |
| `DELETE` | `/v1/products/{id}` | Delete a product |

## Run locally

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Then start the API with the local profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Useful local URLs:

- API: `http://localhost:8080/v1/products`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`

Local PostgreSQL JDBC URL:

```text
jdbc:postgresql://localhost:5432/productdb
```

## Check Swagger docs

Start the application locally:

```bash
mvn spring-boot:run
```

Then open Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

The raw OpenAPI JSON is available at:

```text
http://localhost:8080/v3/api-docs
```

If you are running the application with Docker Compose, the Swagger and OpenAPI URLs are the same because the API is exposed on local port `8080`.

## Run with Docker Compose

```bash
docker compose up --build
```

This starts:

- PostgreSQL on port `5432`
- Product API on port `8080`

Docker Compose runs the API with the `local` Spring profile and PostgreSQL.

## Run tests

Run unit tests and generate the unit-test coverage report:

```bash
mvn test
```

Unit coverage report:

```text
target/site/jacoco-unit/index.html
```

Run unit and integration tests:

```bash
mvn verify
```

API route/scenario coverage report:

```text
target/reports/api-scenario-coverage/index.html
```

Run the standalone Docker-backed E2E runner and generate the E2E scenario report:

```bash
mvn -f e2e/pom.xml verify
```

E2E report:

```text
target/reports/e2e-scenario-report/index.html
```

Use a different deployed stage by overriding the base URL:

```bash
mvn -f e2e/pom.xml verify -De2e.skipDocker=true -De2e.base-url=https://api.example.com
```

Configure E2E database access per target environment when tests need to set up data or validate database state directly:

```bash
mvn -f e2e/pom.xml verify \
  -De2e.skipDocker=true \
  -De2e.environment=staging \
  -De2e.base-url=https://staging-api.example.com \
  -De2e.db.enabled=true \
  -De2e.db.host=staging-db.example.com \
  -De2e.db.port=5432 \
  -De2e.db.name=productdb \
  -De2e.db.username=product_user \
  -De2e.db.password=change-me
```

E2E DB access accepts system properties or environment variables:

| Setting | Environment variable | Default |
| --- | --- | --- |
| `e2e.environment` | `E2E_ENVIRONMENT` | `local` in the E2E runner |
| `e2e.base-url` | `E2E_BASE_URL` | `http://localhost:8080` |
| `e2e.skipDocker` | `E2E_SKIP_DOCKER` | `false` in the E2E runner |
| `e2e.db.enabled` | `E2E_DB_ENABLED` | `auto` |
| `e2e.db.url` | `E2E_DB_URL` | Optional full JDBC URL override |
| `e2e.db.host` | `E2E_DB_HOST` | `localhost` |
| `e2e.db.port` | `E2E_DB_PORT` | `5432` |
| `e2e.db.name` | `E2E_DB_NAME` | `productdb` |
| `e2e.db.username` | `E2E_DB_USERNAME` | `product_user` for Docker-backed local runs; otherwise unset |
| `e2e.db.password` | `E2E_DB_PASSWORD` | `product_password` for Docker-backed local runs; otherwise unset |
| `e2e.report.path` | `E2E_REPORT_PATH` | `../target/reports/e2e-scenario-report/index.html` from the `e2e` project |

`e2e.base-url` controls which API host the E2E tests call. `e2e.db.*` controls which database the E2E tests connect to for setup and assertions. `e2e.db.url` wins when supplied; otherwise the helper builds `jdbc:postgresql://{host}:{port}/{name}`. `e2e.db.enabled=auto` enables direct DB access for Docker-backed local runs and for runs where a DB URL or DB host/name/port is explicitly configured. For staging or any shared environment, prefer passing explicit staging DB credentials and keep setup/assertions scoped to test-owned data such as SKUs starting with `E2E-`.

Unit tests use Mockito and JUnit 5 to validate class-level behavior in isolation. Integration tests use Spring Boot, MockMvc, Flyway, Named JDBC, and Testcontainers PostgreSQL. E2E tests live in the separate `e2e` Maven project and run as a black-box client against a running application plus the configured database.

Use `target/reports/api-scenario-coverage/index.html` to answer API-ownership questions such as:

- Which services have integration coverage?
- Which routes are covered?
- Which request/response scenarios are covered?
- Which discovered routes are untested?

The API scenario coverage report is generated during `mvn verify`. It is not hand-maintained. Spring MVC route mappings provide the route inventory, generated OpenAPI annotations provide the expected non-5xx response contract, and integration tests annotated with `@ApiScenario` provide the executed request/response results. Passing scenarios are marked `PASSED`; failing scenarios are marked `FAILED` with the failure reason; discovered routes without any passing scenario are marked `UNTESTED`; spec-defined non-5xx responses without a matching scenario are marked `MISSING`.

## Testing strategy

This project separates test intent by confidence level:

| Test type | Purpose | Measurement |
| --- | --- | --- |
| Unit tests | Validate business rules in isolation, especially service-layer decisions. | JaCoCo report at `target/site/jacoco-unit/index.html`. |
| Integration tests | Validate Spring wiring across controller, service, repository, Flyway, and database behavior. | Auto-generated route scenario coverage at `target/reports/api-scenario-coverage/index.html`. |
| E2E tests | Validate real product-service workflows against a running application. | Auto-generated workflow scenario report at `target/reports/e2e-scenario-report/index.html`. |

The standalone `e2e` Maven project starts the application with Docker Compose by default, waits for `/actuator/health`, fetches routes from `/v3/api-docs`, runs only E2E workflow tests against the configured base URL, generates the E2E HTML report, and stops the Docker Compose services afterward. The service module's `mvn verify` does not run E2E tests. Set `-De2e.skipDocker=true` when testing an already-running local app or deployed stage. E2E reports scenario results only; line coverage is intentionally not reported.

## Example requests

Create a product:

```bash
curl -i -X POST http://localhost:8080/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Mechanical Keyboard",
    "sku": "KEY-001",
    "description": "Compact keyboard with tactile switches",
    "price": 129.99,
    "active": true
  }'
```

List products:

```bash
curl "http://localhost:8080/v1/products?page=0&size=20&sortBy=name&direction=ASC"
```

Update a product:

```bash
curl -i -X PUT http://localhost:8080/v1/products/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "4K Monitor",
    "sku": "MON-4K-001",
    "description": "Updated display",
    "price": 299.99,
    "active": true
  }'
```

Delete a product:

```bash
curl -i -X DELETE http://localhost:8080/v1/products/1
```

## Error response style

The API returns the generated OpenAPI `ErrorResponse` model for consistent errors.

Example validation error:

```json
{
  "type": "https://api.example.com/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more request fields are invalid.",
  "path": "/v1/products",
  "errors": {
    "name": "size must be between 1 and 120",
    "sku": "size must be between 1 and 64"
  }
}
```

## Observability

The service participates in a gateway-friendly correlation ID contract:

- Reads `X-Correlation-Id` when the gateway/client sends it.
- Generates a correlation ID when the header is missing or invalid.
- Returns `X-Correlation-Id` on every response.
- Adds the ID to MDC as `correlationId` so application logs can include it.
- Adds `correlationId` to API error responses.

The gateway should normally create and forward the correlation ID. The service implementation is defensive so direct local calls, tests, and misconfigured gateway paths are still traceable.

## Showcase highlights

- Versioned REST API under `/v1`
- API-first request/response contracts generated from OpenAPI
- Schema-generated validation with field-level error messages
- Global exception handling with RFC 7807-style responses
- Pagination and sorting with defensive sort-property validation
- Unique SKU business rule with `409 Conflict`
- Flyway-managed schema
- Actuator health, info, and metrics endpoints
- Gateway-friendly `X-Correlation-Id` propagation
- Swagger UI for discoverable API documentation
- Docker Compose runtime with PostgreSQL
- Integration tests covering success and failure paths
