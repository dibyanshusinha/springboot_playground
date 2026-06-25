# Spring Boot Playground

A minimal Spring Boot sample application with REST endpoints and integration tests.

## Project structure

- `src/main/java` — application source
- `src/test/java` — test source
- `pom.xml` — Maven build configuration

## Requirements

- Java 8+ or compatible JDK
- Maven 3.x

## Running the application

```bash
mvn spring-boot:run
```

The application starts on `http://localhost:8080` by default.

## Running tests

Run all tests:

```bash
mvn test
```

Run only the sample test class:

```bash
mvn -Dtest=SampleApplicationTests test
```

## Endpoints

- `GET /defaultHello`
- `GET /defaultHello?message=YourMessage`
- `POST /customHello?message=YourMessage`

## Expected behavior

- `GET /defaultHello`
  - returns `{"echo":"Hello World!"}`
- `GET /defaultHello?message=HackerRank!`
  - returns `{"echo":"Hello HackerRank!"}`
- `POST /customHello?message=Custom Hello World!`
  - returns `{"echo":"Custom Custom Hello World!"}`
- `POST /customHello` without `message`
  - returns HTTP `400 Bad Request`

## Notes

- `SampleApplication` must be located at:
  - SampleApplication.java
- `SampleApplicationTests` must be located at:
  - SampleApplicationTests.java

## Troubleshooting

- If you see `Unable to find a @SpringBootConfiguration`:
  - verify SampleApplication.java is in the correct package directory
  - verify the package declaration matches the file path
  - verify the test class is under java

- If Maven does not detect tests:
  - confirm the test folder is java
  - confirm test class names follow the `*Test` or `*Tests` pattern

## Maven dependencies

The project uses:

- `spring-boot-starter`
- `spring-boot-starter-web`
- `spring-boot-starter-test` (test scope)
- `h2` (test scope)


