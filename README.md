# learn-spring / fleet-api

Spring Boot 4 port of the fleetkeeper API — the backend-learning track
item. Same domain as the Python/Go/Vue projects, so the framework is the
only new thing.

## What it demonstrates

| Concern | Mechanism | File |
|---|---|---|
| REST routing | `@RestController` + annotation scanning (vs Go's explicit registration) | `FleetController.java` |
| Persistence | Spring Data JPA: method-name-derived SQL, zero SQL written | `ServiceRepository.java` |
| Validation | Bean Validation annotations (`@Min/@Max/@NotBlank`) reject bad input at the door | `ServiceEntity.java` |
| Auth middleware | JWT filter: login issues, everything else requires `Authorization: Bearer` | `JwtAuth.java` |
| Dependency injection | constructor injection, IoC container | all |
| Database | SQLite via `sqlite-jdbc` + community dialect — zero install | `application.properties` |

## Run

```
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8090"
```

## Verified end-to-end (8/8)

login → token; 401 without token; 200 with; create 201; duplicate port
409; port 99999 rejected 400; PATCH heartbeat persisted; delete 200.
