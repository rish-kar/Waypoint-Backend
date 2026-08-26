# Repository Structure and Change Workflow

## Repository map

```text
Waypoint-Backend/
├── src/main/java/com/waypoint/backend/
│   ├── controller/        # HTTP adapters
│   ├── service/           # business workflows
│   ├── repository/        # Spring Data persistence
│   ├── model/             # entities and API/domain models
│   ├── security/          # security config/JWT/admin/rate/body limits
│   └── ...                # configuration, clients and web infrastructure
├── src/main/resources/
│   ├── application*.yml
│   └── db/migration/      # Flyway
├── src/test/              # unit/application/security tests
├── postman/               # generated API collection
├── scripts/               # collection synchronization tooling
├── doc/                   # engineering documentation
├── Dockerfile
├── docker-compose.yml
└── .github/workflows/     # CI, CodeQL etc.
```

## Adding an endpoint

1. Define request/response models with validation.
2. Add controller mapping under the correct API group.
3. Put business rules in a service.
4. Add repository queries only when persistence is required.
5. Decide public/JWT/admin security ownership explicitly.
6. Add error mapping and rate/body considerations.
7. Add tests for success, validation, unauthenticated and unauthorized cases.
8. Update Postman source and regenerate collection.
9. Update API/security documentation.

## Changing persistence

Add a new Flyway migration and keep JPA mappings compatible. Run both H2/default and PostgreSQL/Testcontainers verification. Never rely on Hibernate schema generation in production.

## Changing provider integration

Keep provider HTTP details in the client layer. Add mocked client tests plus real test-mode manual procedure. Webhook changes must preserve raw-body signature verification and idempotency.

## Changing security

Security changes require tests for both ordered chains. Review CORS, HTTPS, body limit and Redis rate behavior. Never make admin endpoints reachable with ordinary customer JWTs.

## Pre-PR commands

```bash
python scripts/sync_postman_collection.py
mvn clean verify
mvn -Ppostgres-it verify
```

Confirm `git diff` has no unexpected generated Postman changes after synchronization.

## Dependency changes

CI dependency review fails pull requests that introduce vulnerabilities at configured severity when the GitHub dependency graph is available. CodeQL/dependency automation should complement, not replace, application-level security testing.

## Documentation rule

Update `doc/README.md` links when adding a new responsibility document. Prefer editing an existing focused document to creating a second overlapping description of the same flow.
