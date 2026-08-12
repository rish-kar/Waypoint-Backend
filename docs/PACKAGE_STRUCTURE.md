# Backend package structure

The backend follows a layered Spring Boot package structure. Each layer is split into feature-specific subpackages.

```text
com.waypoint.backend
├── controller
│   ├── advice
│   ├── auth
│   ├── billing
│   ├── entitlement
│   ├── user
│   └── webhook
├── model
│   ├── auth
│   ├── billing
│   ├── common
│   ├── entitlement
│   ├── plan
│   ├── subscription
│   ├── user
│   └── webhook
├── service
│   ├── auth
│   ├── billing
│   ├── entitlement
│   ├── plan
│   ├── subscription
│   ├── user
│   └── webhook
├── repository
│   ├── plan
│   ├── subscription
│   ├── user
│   └── webhook
├── utilities
│   ├── client
│   │   ├── google
│   │   └── lemonsqueezy
│   └── exception
├── config
│   ├── application
│   ├── auth
│   ├── billing
│   ├── client
│   └── logging
└── security
    ├── config
    └── jwt
```

- `controller`: HTTP endpoints and exception advice.
- `model`: entities, request/response records, enums and value objects.
- `service`: business rules and orchestration.
- `repository`: Spring Data persistence interfaces.
- `utilities`: external API clients and shared exceptions.
- `config`: application properties, startup validation, HTTP clients and logging configuration.
- `security`: Spring Security and JWT handling.
