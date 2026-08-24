# [User Auth 🔑] Add JWT Login & Registration Endpoints

## Type of Change
- [x] Feature
- [ ] Fix
- [ ] Refactor

## Description
Implements the User Authentication module for the Food Delivery Backend (Spring Boot). Adds two public REST endpoints — `POST /api/v1/auth/register` and `POST /api/v1/auth/login` — using Spring Security + JWT (stateless, no sessions), BCrypt password hashing, request validation, and a global exception handler for consistent error responses.

## Key Changes

**Entities & Repository**
- `entity/User.java` — JPA entity (`id`, `fullName`, `email`, `password`, `phone`, `role`, `enabled`, `createdAt`, `updatedAt`), unique constraint on `email`.
- `entity/Role.java` — `CUSTOMER`, `RESTAURANT_OWNER`, `ADMIN`.
- `repository/UserRepository.java` — `findByEmail`, `existsByEmail`.

**DTOs**
- `dto/RegisterRequest.java` — validated (`@NotBlank`, `@Email`, password pattern: min 8 chars, letter+number, Egyptian phone regex). Optional `role` field accepts `CUSTOMER` or `RESTAURANT_OWNER` (case-insensitive); defaults to `CUSTOMER` when omitted. `ADMIN` is rejected — admin accounts are never self-assignable through public registration.
- `dto/LoginRequest.java` — email + password validation.
- `dto/AuthResponse.java` — returns `accessToken`, `tokenType`, `userId`, `fullName`, `email`, `role`.
- `dto/ApiError.java` — standard error payload (timestamp, status, message, path, details).

**Security / JWT**
- `security/JwtService.java` — generates/validates HS256 JWTs (24h expiry, configurable).
- `security/UserPrincipal.java`, `security/UserDetailsServiceImpl.java` — Spring Security `UserDetails` integration.
- `security/JwtAuthFilter.java` — `OncePerRequestFilter` parsing `Authorization: Bearer <token>`.
- `config/SecurityConfig.java` — stateless session policy, `/api/v1/auth/**` public, BCrypt encoder, `DaoAuthenticationProvider`.
- `config/JpaAuditingConfig.java` — enables `createdAt`/`updatedAt` auto-population.

**Business Logic & API**
- `service/AuthService.java` — `register()` (checks duplicate email, hashes password, defaults role to `CUSTOMER`), `login()` (authenticates via `AuthenticationManager`, issues JWT).
- `controller/AuthController.java` — `POST /api/v1/auth/register` (201 Created), `POST /api/v1/auth/login` (200 OK).

**Error Handling**
- `exception/EmailAlreadyExistsException.java` → 409 Conflict
- `exception/InvalidCredentialsException.java` → 401 Unauthorized
- `exception/GlobalExceptionHandler.java` — `@RestControllerAdvice` covering validation errors (400), bad credentials (401), duplicate email (409), fallback (500).

**Config & Tests**
- `application.yml` — DB, JPA, JWT secret/expiration via env vars.
- `application-test.yml` — H2 in-memory profile for tests.
- `AuthControllerTest.java` — register success, duplicate email conflict, invalid email validation, wrong-password login → 401.

## How to Test

### Prerequisites
- PostgreSQL running locally (or update `application.yml` datasource), or run tests against H2 with `-Dspring.profiles.active=test`.
- Set env vars (optional, defaults exist): `JWT_SECRET`, `DB_USERNAME`, `DB_PASSWORD`.

### Run the app
```bash
mvn spring-boot:run
```

### 1. Register a new user
```
POST http://localhost:8080/api/v1/auth/register
Content-Type: application/json

{
  "fullName": "Ahmed Ali",
  "email": "ahmed.ali@example.com",
  "password": "Passw0rd123",
  "phone": "01012345678",
  "role": "RESTAURANT_OWNER"
}
```
`role` is optional — omit it (or send `"CUSTOMER"`) for a normal customer account. Sending `"ADMIN"` returns `400 Bad Request`.

**Expected:** `201 Created`
```json
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "userId": 1,
  "fullName": "Ahmed Ali",
  "email": "ahmed.ali@example.com",
  "role": "CUSTOMER"
}
```

### 2. Register with an existing email
Repeat the same request → **Expected:** `409 Conflict` with `ApiError` body.

### 3. Register with invalid data (e.g. bad email, weak password)
```json
{ "fullName": "A", "email": "not-an-email", "password": "123", "phone": "123" }
```
**Expected:** `400 Bad Request` with a `details` array listing each field error.

### 4. Login with correct credentials
```
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "email": "ahmed.ali@example.com",
  "password": "Passw0rd123"
}
```
**Expected:** `200 OK` with a fresh `accessToken`.

### 5. Login with wrong password
Same request with a wrong password → **Expected:** `401 Unauthorized`.

### 6. Use the token on a protected endpoint (once available)
```
Authorization: Bearer <accessToken>
```

### Run automated tests
```bash
mvn test
```

## Checklist
- [x] Passwords hashed with BCrypt (never stored/returned in plain text)
- [x] Input validation on all fields
- [x] Consistent error response shape (`ApiError`)
- [x] No secrets committed (JWT secret sourced from env var)
- [x] Unit/integration tests included
