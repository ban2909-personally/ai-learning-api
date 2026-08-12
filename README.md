# AI Learning Platform API

Spring Boot backend for the B2C AI Learning Platform.

## Stack

- Java 21 and Spring Boot 3.5
- Spring Security, OAuth2 Resource Server and JWT
- Spring Data JPA with Hibernate
- Flyway migration-first schema management
- PostgreSQL and Redis
- JUnit 5 and Testcontainers

## Run locally

Start PostgreSQL and Redis from the `ai-learning-platform` infrastructure repository, then run:

```powershell
mvn spring-boot:run
```

The API listens on `http://localhost:8080`; health is available at `/actuator/health`.

## Verify

```powershell
mvn verify
```

Database schema changes must be delivered through Flyway migrations. Hibernate is configured with `ddl-auto=validate` and must not mutate the schema.
