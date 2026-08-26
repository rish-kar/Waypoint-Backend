# Admin Authentication, TOTP and Roles

## Separate admin identity

Administrative endpoints do not accept a normal Waypoint user JWT. `/api/v1/admin/**` is selected by the higher-priority admin Spring Security chain and authenticated with HTTP Basic against `AdminAccountService`.

Admin credentials are therefore operational credentials, not customer identities.

## Roles

Two roles are used by the security configuration:

- `ADMIN` — read access to ordinary admin inspection endpoints.
- `SUPER_ADMIN` — all admin mutations plus admin-account management.

All POST, PUT, PATCH and DELETE requests under `/api/v1/admin/**` require `SUPER_ADMIN`. `/api/v1/admin/accounts/**` also requires `SUPER_ADMIN` for reads.

## Admin accounts

Admin accounts are persisted and managed through `AdminAccountController`:

- list accounts;
- create account;
- patch account.

Passwords are processed through Spring Security's delegating `PasswordEncoder`; never store plaintext passwords.

## Production TOTP

`SecurityConfig` adds `AdminTotpFilter` after Basic authentication only in the `prod` profile. Production callers supply `X-Admin-TOTP` in addition to valid Basic credentials.

Configuration includes:

```text
ADMIN_TOTP_SECRET
ADMIN_TOTP_ENCRYPTION_KEY
```

Treat both as secrets. The encryption key used to protect stored/configured TOTP material must be replaced from the development default before production.

## Transport security

Production admin traffic requires HTTPS through Spring Security `requiresChannel`. HTTP Basic credentials and TOTP codes must never traverse plaintext HTTP.

## Auditability

Administrative mutations are written to the admin audit trail. The audit model lets operators filter by admin ID, action, resource type/resource ID and timestamps. When adding a new mutation, add audit behavior as part of the feature rather than as an afterthought.

## Bootstrap/configuration

`ADMIN_ID` and `ADMIN_PASSWORD` exist as environment/configuration inputs for admin setup/operation. Production validation must reject empty or development-placeholder credentials. Do not place them in source-controlled YAML, shell history or Postman exports.

## Testing requirements

Admin security tests should include:

- missing Basic credentials -> 401;
- invalid credentials -> 401;
- `ADMIN` attempting mutation -> 403;
- `SUPER_ADMIN` mutation -> allowed;
- account endpoints denied to ordinary `ADMIN`;
- production TOTP missing/invalid -> denied;
- audit event created for successful controlled mutation;
- credentials/TOTP absent from logs.
