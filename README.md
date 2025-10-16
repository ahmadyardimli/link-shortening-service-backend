# Link Shortening Service — Backend (Spring Boot)

A production-style backend for a URL shortener built with **Spring Boot 3.5**, **Spring Security (JWT)**, **H2 (file DB)**, **JPA/Hibernate**, **OpenAPI/Swagger**, **per-endpoint rate limiting**, and **click analytics**.
This README covers **only the backend**: how to run it, how security works, and how to use the API via **Swagger UI** and **Postman** with full request/response JSONs.

---

## What this backend does

* **Shorten URLs** with optional **custom alias** and **expiration (days)**.
* **Bitly-like reuse**: by default it **reuses an existing, non-expired** short link for the same **user + original URL**, unless you explicitly disable reuse.
* **Redirect** short codes to the original URL.
* **Click analytics**: increments a counter on each redirect and exposes statistics.
* **JWT auth** with **refresh-token rotation** (only one active refresh token per user).
* **Rate limit** for `POST /shorten` (per-IP for anonymous requests, per-user for authenticated).
* **OpenAPI/Swagger UI** for interactive API docs and testing.
* **Postman**-ready (screens and example bodies provided).
* **CORS** opened for common local dev origins.

---

## Architecture (high level)

```
Client (Swagger UI / Postman / any HTTP client)
        ↓
Spring Boot REST API
├─ Security: JwtAuthFilter (reads Authorization: Bearer <jwt>, sets principal = userId)
├─ RateLimitFilter (for POST /shorten; per-IP or per-user)
├─ Controllers (Auth, URL Shortener)
├─ Services (Auth, Refresh Tokens, URL Shortener, User)
├─ JPA Repositories (User, Url, RefreshToken)
└─ H2 (file DB) persisted under ./data/
```

Key components:

* **`JwtAuthFilter`**: validates JWT access tokens and attaches `userId` to the security context.
* **`RefreshTokenService`**: issues/rotates one refresh token per user, stored as a **SHA-256 hash** in DB.
* **`RateLimitFilter`**: in-memory sliding window counters for `POST /shorten`.
* **`UrlSafetyValidator`**: blocks localhost/private/unsafe targets.
* **`GlobalExceptionHandler`**: consistent JSON errors.

---

## Tech stack

* **Java 17+**, **Maven**
* **Spring Boot 3.5**, **Spring Security**
* **Spring Data JPA/Hibernate**
* **H2** (file DB)
* **JJWT 0.11.5** (JWT)
* **springdoc-openapi 2.8.x** (Swagger UI)
* **Apache Commons Validator** (URL format validation)
* **Lombok**

---

## Getting started

### 1) Prerequisites

* **Java 17+**
* **Maven 3.9+**
* A **base64**-encoded secret for signing JWTs (≥ 32 bytes recommended):

```bash
# Example: generate a 256-bit base64 key
openssl rand -base64 32
```

### 2) Environment variables

Set these before running (IDE “Run Configuration → Environment variables” works great):

* **Required**

  * `JWT_SECRET` — base64 HMAC secret used to sign access tokens.

* **Optional (dev defaults in `application.properties`)**

  * `BASE_URL` — base used when constructing returned short links (e.g. `http://short.ly`).
  * `SERVER_PORT` — server port (default `8080`).
  * Rate limit knobs:

    * `RATELIMIT_WINDOW_SECONDS` (default `3600`)
    * `RATELIMIT_ANON_MAX` (default `30`)
    * `RATELIMIT_AUTH_MAX` (default `500`)

**Example (IDE run config):**

```
BASE_URL=http://short.ly
SERVER_PORT=8080
JWT_SECRET=<your_base64_secret>
```

### 3) Run

```bash
mvn spring-boot:run
# API: http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

---

## Configuration reference

`src/main/resources/application.properties` (key parts):

```properties
spring.application.name=link-shortening-service
server.port=${SERVER_PORT:8080}

# H2 file DB (persists across restarts)
spring.datasource.url=jdbc:h2:file:./data/urlshortener
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect

# Short links
url.shortener.base-url=${BASE_URL:http://short.ly}
url.shortener.code-length=6

# Logging (helpful in dev)
logging.level.com.urlshortener=DEBUG
logging.level.org.springframework.security=DEBUG

# JWT (configure per your needs)
jwt.secret=${JWT_SECRET}
jwt.access.expiration-ms=9900000000      # example: long-lived in dev
jwt.refresh.expiration-seconds=18000000  # example: long-lived in dev

# Swagger / OpenAPI
springdoc.api-docs.enabled=true
springdoc.swagger-ui.enabled=true

# Rate limit (can be overridden via env)
# ratelimit.window.seconds=${RATELIMIT_WINDOW_SECONDS:3600}
# ratelimit.anon.max=${RATELIMIT_ANON_MAX:30}
# ratelimit.auth.max=${RATELIMIT_AUTH_MAX:500}
```

> **Note:** For production, choose sensible token TTLs (e.g., access 15–60 minutes; refresh 7–30 days) and consider a distributed store for rate limiting.

---

## Database (H2)

* **File location:** `./data/urlshortener` (created at runtime).
* **Console:** `http://localhost:8080/h2-console`

  * JDBC URL: `jdbc:h2:file:./data/urlshortener`
  * User: `sa` (no password)

**Entities**

* `User(id, email UNIQUE, passwordHash)`
* `Url(id, originalUrl, shortCode UNIQUE, createdAt, clickCount, customAlias?, expiresAt?, userId?)`
* `RefreshToken(id, user, tokenHash UNIQUE, expiresAt)`

---

## CORS

Configured in `CorsConfig` to allow typical local dev origins:

```
http://localhost, http://127.0.0.1,
:5500, :8000, :3000
```

Methods: `GET, POST, PUT, DELETE, OPTIONS`.
Headers: `*`.
Credentials allowed. TTL: 3600s.

---

## Security, JWT & token flow

* **Stateless** security; sessions are disabled.
* **Public endpoints**: `/auth/**`, `/shorten`, `/{code}`, `/stats/**`, `/h2-console/**`, `/swagger-ui/**`, `/v3/api-docs/**`.
* **Protected endpoints**: `/users/**` (require `Authorization: Bearer <jwt>`).

### Access token

* Signed with HS256 using `JWT_SECRET` (base64 string).
* Carries `sub = userId`.
* Added to responses with the **`Bearer ` prefix already attached**:

  ```
  "accessToken": "Bearer eyJhbGciOiJIUzI1..."
  ```

  Send it as-is:

  ```
  Authorization: Bearer <jwt>
  ```

### Refresh token (rotation)

* Stored **hashed (SHA-256)** in DB, one active token per user.
* On `/auth/refresh`, the incoming token is validated (by hash + expiry) and **rotated** (old becomes invalid; a **new** raw token is returned).
* On refresh failure (invalid/expired), you must re-login.

### Error mapping

* Auth flow failures (`IllegalStateException`) are mapped to **401** with a uniform error body by `GlobalExceptionHandler`.

---

## Rate limiting

* Implemented in `RateLimitFilter`.
* **Scope:** `POST /shorten` only.
* **Anonymous:** key = client IP (`X-Forwarded-For` / `X-Real-IP` fallback to `remoteAddr`).
* **Authenticated:** key = `userId`.
* Defaults (overridable): `window=3600s`, `anon.max=30`, `auth.max=500`.
  Exceeding returns **429 Too Many Requests** with JSON error body.

---

## API reference (requests & responses)

> Base URL (local): `http://localhost:8080`
> JSON header: `Content-Type: application/json`
> Auth header (protected routes): `Authorization: Bearer <jwt>`

### 1) Register — `POST /auth/register` *(public)*

**Request**

```json
{
  "email": "user@example.com",
  "password": "test123"
}
```

**200 OK**

```json
{
  "accessToken": "Bearer eyJhbGciOiJIUzI1...",
  "refreshToken": "92571300-1c3f-4cf9-a8d0-1f90d1aa8a45",
  "userId": 8
}
```

**400 Bad Request** (validation)

```json
{
  "timestamp": "2025-10-16T18:35:10.123",
  "status": 400,
  "error": "Validation Failed",
  "errors": { "email": "must be a well-formed email address" },
  "path": "/auth/register"
}
```

**401 Unauthorized** (duplicate email mapped to 401 for consistent auth UX)

```json
{
  "timestamp": "...",
  "status": 401,
  "error": "Unauthorized",
  "message": "Email already in use",
  "path": "/auth/register"
}
```

---

### 2) Login — `POST /auth/login` *(public)*

**Request**

```json
{
  "email": "user@example.com",
  "password": "test123"
}
```

**200 OK** — same shape as register.
**401 Unauthorized**

```json
{
  "timestamp": "...",
  "status": 401,
  "error": "Unauthorized",
  "message": "Incorrect email or password",
  "path": "/auth/login"
}
```

---

### 3) Refresh — `POST /auth/refresh` *(public)*

**Request**

```json
{
  "refreshToken": "92571300-1c3f-4cf9-a8d0-1f90d1aa8a45"
}
```

**200 OK**

```json
{
  "accessToken": "Bearer eyJhbGciOiJIUzI1...",
  "refreshToken": "7fd68d71-f707-4641-8633-f37d361fa9ba",
  "userId": 8
}
```

**401 Unauthorized** (invalid/expired)

```json
{
  "timestamp": "...",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid refresh token",
  "path": "/auth/refresh"
}
```

---

### 4) Create short URL — `POST /shorten` *(public; better when authenticated)*

**Validation rules**

* `url` — required, ≤ 2048 chars; must be `http`/`https` and pass safety checks (no localhost/private IPs).
* `customAlias` — optional, **max 10 chars**, pattern: `^[a-zA-Z0-9-_]*$`, must be globally unique (also cannot clash with existing short codes).
* `expirationDays` — optional, `null` or ≤ 0 → no expiry.
* `reuseExisting` — optional; **default true**; when true and a non-expired record exists for `(userId, originalUrl)`, the **existing** short code is returned.

**Request (minimal)**

```json
{
  "url": "https://developer.mozilla.org/en-US/Web/HTTP"
}
```

**Request (all options)**

```json
{
  "url": "https://www.linkedin.com/search/results/all/?fetchDeterministicClustersOnly=true&heroEntityKey=urn%3Ali%3Aorganization%3A30846&keywords=spacex&origin=RICH_QUERY_SUGGESTION&position=0&searchId=49d8cae9-6bf2-4759-be23-137518f1f213&sid=NwN&spellCorrectionEnabled=false",
  "customAlias": "spacex_l1",
  "expirationDays": 14,
  "reuseExisting": true
}
```

**200 OK**

```json
{
  "shortCode": "oPnjBU",
  "shortUrl": "http://short.ly/oPnjBU"
}
```

**400 Bad Request** (invalid URL, bad alias, unsafe)

```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid URL format: https://...",
  "path": "/shorten"
}
```

**400 Bad Request** (alias too long / pattern)

```json
{
  "path": "/shorten",
  "error": "Validation Failed",
  "errors": {
    "customAlias": "Custom alias is too long"
  },
  "timestamp": "2025-10-16T19:22:23.861331",
  "status": 400
}
```

**409 Conflict** (alias already exists)

```json
{
  "timestamp": "...",
  "status": 409,
  "error": "Conflict",
  "message": "Request could not be completed due to a data constraint",
  "path": "/shorten"
}
```

**429 Too Many Requests** (rate limit)

```json
{
  "timestamp": "...",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again later.",
  "path": "/shorten"
}
```

---

### 5) Redirect — `GET /{code}` *(public)*

**302 Found** with `Location: <originalUrl>`
**404 Not Found**

```json
{
  "timestamp": "...",
  "status": 404,
  "error": "Not Found",
  "message": "Short URL not found: abcd12",
  "path": "/abcd12"
}
```

**410 Gone** (expired)

```json
{
  "timestamp": "...",
  "status": 410,
  "error": "Gone",
  "message": "This short URL has expired",
  "path": "/abcd12"
}
```

---

### 6) Public stats by code — `GET /stats/{code}` *(public)*

* Optional `userId` query param: when provided and the URL belongs to another user, returns **403/401**.

**200 OK**

```json
{
  "shortCode": "oPnjBU",
  "originalUrl": "https://developer.mozilla.org/en-US/Web/HTTP",
  "clickCount": 3,
  "createdAt": "2025-10-16T16:52:30.861641",
  "expiresAt": "2025-10-26T16:52:30.85982",
  "customAlias": null
}
```

**403 Forbidden** (cross-user gated)

```json
{
  "timestamp": "...",
  "status": 403,
  "error": "Forbidden",
  "message": "You don't have permission to view these statistics",
  "path": "/stats/oPnjBU"
}
```

---

### 7) My stats — `GET /users/me/stats` *(protected)*

**Headers**

```
Authorization: Bearer <jwt>
```

**200 OK**

```json
{
  "totalUrls": 2,
  "totalClicks": 3,
  "urls": [
    {
      "shortCode": "4gGtRF",
      "originalUrl": "https://www.spacex.com",
      "clickCount": 1,
      "createdAt": "2025-10-16T16:32:10.000000",
      "expiresAt": null,
      "customAlias": null
    },
    {
      "shortCode": "inST3J",
      "originalUrl": "https://aliexpress.ru",
      "clickCount": 0,
      "createdAt": "2025-10-16T16:31:12.000000",
      "expiresAt": null,
      "customAlias": null
    }
  ]
}
```

**401 Unauthorized** (missing/invalid token)

```json
{
  "timestamp": "2025-10-16T17:28:33.970+00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "No message available",
  "path": "/users/me/stats"
}
```

---

### 8) Self-only stats by ID — `GET /users/{userId}/stats` *(protected)*

* The `userId` in the path **must match** the authenticated user’s ID.

**403 Forbidden / 401 Unauthorized** (mismatch or missing token)

```json
{
  "timestamp": "...",
  "status": 403,
  "error": "Forbidden",
  "message": "You don't have permission to view these statistics",
  "path": "/users/7/stats"
}
```

**200 OK** — same shape as “My stats”.

---

## Error model (uniform JSON)

All errors follow a consistent structure:

```json
{
  "timestamp": "2025-10-16T14:46:22.63+00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Incorrect email or password",
  "path": "/auth/login"
}
```

**Validation errors** collect field messages:

```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Validation Failed",
  "errors": {
    "url": "URL is required",
    "customAlias": "Custom alias can only contain letters, numbers, hyphens, and underscores"
  },
  "path": "/shorten"
}
```

Common statuses: `400` invalid input • `401` unauthorized • `403` forbidden • `404` not found • `410` gone • `409` conflict • `429` too many requests • `500` unexpected.

---

## Using Swagger UI

**Open**: `http://localhost:8080/swagger-ui.html`

Typical flow in the UI:

1. **Register/Login** to get tokens.
   ![Swagger register UI 1](docs/swagger/auth_register_ui1.png)
   ![Swagger register UI 2](docs/swagger/auth_register_ui2.png)

2. **Authorize** (top-right “Authorize” button) with your **access token** (omit `Bearer ` if the UI asks only for token; if it expects the full value, paste as provided).
   ![Swagger add auth](docs/swagger/swagger_add_auth.png)
   ![Swagger authorized](docs/swagger/swagger_authorized.png)

3. Call protected endpoints like **`GET /users/me/stats`**.
   ![Protected endpoint req/resp](docs/swagger/protected_endpoint_req_and_res.png)

4. Explore/create short links via **`POST /shorten`** and inspect responses.
   ![Shorten before request](docs/swagger/shorten_before_request.png)
   ![Shorten response](docs/swagger/shorten_response.png)
   ![Shorten stats](docs/swagger/shorten_stats.png)
   ![Click req/resp](docs/swagger/click_req_res.png)
   ![Swagger UI 1](docs/swagger/swagger_ui1.png)
   ![Swagger UI 2](docs/swagger/swagger_ui2.png)
   ![Swagger UI 3](docs/swagger/swagger_ui3.png)

---

## Using Postman

Typical steps (matching provided screenshots):

1. **Register** → **Login** to receive `accessToken` and `refreshToken`.
   ![Register request/response](docs/postman/register_request_response.png)
   ![Login request/response](docs/postman/login_req_res.png)

2. **Add Authorization header** with the returned access token **as-is** (already prefixed with `Bearer `).
   ![Add auth header after login](docs/postman/adding_auth_to_header_after_login.png)

3. **Create short links**:

   * Minimal body:
     ![Shorten minimal](docs/postman/url_shorten_minimal.png)
   * All options (alias/expiry/reuse):
     ![Shorten all options](docs/postman/url_shorten_with_all_options_included.png)
   * Validation error (alias too long):
     ![Alias too long](docs/postman/url_shorten_with_too_long_error_response.png)
   * Alias conflict:
     ![Alias already exists](docs/postman/custome_alias_already_exist_error.png)

4. **Stats & redirects**:

   * Click (redirect) request & response:
     ![Click redirect req/resp](docs/postman/short_code_click_req_res.png)
   * Stats by code (with and without alias):
     ![Stats by code](docs/postman/stats_short_code_req_res.png)
     ![Stats without alias](docs/postman/stats_for_short_code_without_alias.png)

5. **User stats (protected)**:

   * First call to `GET /users/me/stats`:
     
     ![First call users/me stats](docs/postman/first_call_users_me_stats.png)
   * All stats for authenticated user:
     ![All stats user](docs/postman/auth_user_all_stats.png)
   * Unauthorized example (expired/missing token):
     ![Unauthorized example](docs/postman/error_response_for_protected_me_stats_when_token_expired.png)

6. **Token refresh** (rotate refresh token and get a new access token):
   ![Refresh request](docs/postman/refresh_request.png)

7. **More samples**:

   * Create & then shorten again as the same user:
     ![Auth user shorten](docs/postman/auth_user_shorten.png)
   * Click another code:
     ![Click another short code](docs/postman/click_another_short_code.png)

> **Tip:** In Postman you can create collection variables (`accessToken`, `refreshToken`, `userId`) and use them across requests. Send the access token as the full value your backend returns (it already contains the `Bearer ` prefix).

---

## Troubleshooting

* **401 on protected endpoints**

  * Ensure header is exactly `Authorization: Bearer <jwt>`.
  * If the token expired, use `/auth/refresh` with your `refreshToken` to obtain a new access token; on failure, re-login.

* **CORS errors**

  * Serve the client (if any) from an allowed origin (e.g. `http://127.0.0.1:5500`, `:8000`, `:3000`).

* **H2 “file is locked”**

  * Stop all app instances; delete `./data/urlshortener.lock.db` if present, then restart.

* **Alias conflict**

  * Aliases must be unique and cannot collide with generated codes. You’ll get **409 Conflict** or **400 Validation** depending on the scenario.

* **Rate limit exceeded**

  * `POST /shorten` is limited: **per-IP** when anonymous and **per-user** when authenticated. Adjust with the `ratelimit.*` properties.

---

## License

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://github.com/ahmadyardimli/link-shortening-service-backend/blob/master/LICENSE)  
This project is licensed under the **Apache License 2.0** — see the [LICENSE](https://github.com/ahmadyardimli/link-shortening-service-backend/blob/master/LICENSE) file for details.

