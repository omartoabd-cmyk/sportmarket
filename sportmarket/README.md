# SportMarket

SportMarket is a sports equipment marketplace backend. Milestones 1-6 provide public listing/category reads, registration, login, JWT-protected current-user information, listing CRUD with seller ownership authorization, and secure product photos. Milestone 4 adds a separate React frontend in `../frontend`; see its README for setup and complete browser workflows.

## Stack

Java 21, Spring Boot 4.1.2, Maven, PostgreSQL, Spring Data JPA/Hibernate, Flyway, and Spring Security OAuth2 Resource Server. The existing Spring Initializr entry point (`sportmarket.SportmarketApplication`) is preserved. Milestone 2 adds the Boot-managed Security and OAuth2 Resource Server starters. H2 is test-scoped only; no custom JWT filter or third-party JWT dependency is needed.

## Project layout

The Maven project is the `sportmarket` subfolder of the workspace. Run the commands below from that folder.

- `controller`: `ListingController` and `CategoryController` expose GET routes and delegate to services.
- `service`: `ListingService` filters available listings, looks up individual listings, and maps entities to DTOs. `CategoryService` reads categories alphabetically. Both use read-only transactions.
- `repository`: `ListingRepository`, `CategoryRepository`, and `UserRepository` extend `JpaRepository`. Listing reads fetch category and seller together to avoid repeated lazy-loading queries.
- `entity`: `User`, `Category`, and `Listing` map database tables. `ListingCondition` and `ListingStatus` are stored as enum names. JPA callbacks initialize timestamps and update `updatedAt` when Hibernate updates a listing.
- `dto`: `CategoryResponse` and `ListingResponse` define API JSON. The nested `SellerResponse` contains only ID and full name; email and password hash stay private.
- `exception`: `ListingNotFoundException` signals a missing listing. `GlobalExceptionHandler` returns Problem Detail JSON and handles standard MVC errors, including invalid ID types.
- `config`: `SecurityConfig` defines the security filter chain, password encoder, authentication manager, JWT encoder/decoder, and exact CORS origin list. `SecurityErrorHandler` writes Problem Detail JSON for failures occurring in security filters, before MVC.
- `src/main/resources/db/migration`: versioned SQL owned by Flyway.

## Local PostgreSQL setup

Install PostgreSQL with its command-line tools and start the PostgreSQL service. Use a supported PostgreSQL release, such as PostgreSQL 17. Connect with your administrator account:

```powershell
psql -h localhost -U postgres -d postgres
```

Create an application role and database in `psql`:

```sql
CREATE ROLE sportmarket_app LOGIN;
\password sportmarket_app
CREATE DATABASE sportmarket OWNER sportmarket_app;
```

The `\password` command prompts for a password; choose your own. Do not put it in source control. Exit with `\q`. Verify access:

```powershell
psql -h localhost -U sportmarket_app -d sportmarket -W
```

If `psql` is not on PATH, invoke it from your PostgreSQL installation's `bin` directory. You can perform the equivalent role/database setup in pgAdmin.

## Environment variables and running

Use JDK 21 and verify `java -version` and `mvn -version`. Set `JAVA_HOME` to your JDK 21 installation if necessary.

| Variable | Meaning | Default |
| --- | --- | --- |
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/sportmarket` |
| `DB_USERNAME` | Database role | Required |
| `DB_PASSWORD` | Database password | Required |
| `JWT_SECRET` | Base64-encoded cryptographically random signing key, at least 32 decoded bytes | Required; no fallback |
| `JWT_ISSUER` | Expected issuer when issuing and verifying tokens | `sportmarket` |
| `IMAGE_STORAGE_DIR` | Absolute writable directory for local product photos | `${user.home}/.sportmarket/uploads` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated exact frontend origins, without paths or trailing slashes | Empty; no cross-origin browser access |

In the same PowerShell terminal used to run the application:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/sportmarket"
$env:DB_USERNAME = "sportmarket_app"
$dbCredential = Get-Credential -UserName $env:DB_USERNAME -Message "Enter your local PostgreSQL password"
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
# Generate a random 256-bit signing key without printing it.
$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$jwtRng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$env:JWT_ISSUER = "sportmarket"
$env:CORS_ALLOWED_ORIGINS = "http://localhost:5173"
mvn spring-boot:run
```

With a working Maven wrapper, `./mvnw.cmd spring-boot:run` is equivalent on Windows (`./mvnw spring-boot:run` on macOS/Linux). Maven 3.6.3 or later can run this project directly if the existing wrapper fails to start.

Spring Boot does not automatically load `.env` files. Set these environment variables in the launching terminal or your IDE run configuration. No PostgreSQL credentials or signing keys are hardcoded. The generated key lives in this terminal's environment; do not print, commit, or share it. Use the same securely stored key across application restarts if existing tokens should remain valid. Generating a different key invalidates previously issued tokens. All running instances must use the same key/issuer. Startup rejects malformed Base64 and keys shorter than 32 decoded bytes.

Do not enable request/response body logging, SQL bind-value logging, or security TRACE logging when using real credentials. Auth DTOs redact their `toString()` output, and DevTools automatic debug properties are disabled, web/security logs stay at INFO, and SQL bind logging is disabled to keep rejected passwords and hashes out of verbose logs. Use HTTPS outside local development; JWT signing does not encrypt token contents.

On startup Flyway applies `V1__create_tables_and_seed_categories.sql`, creating `users`, `categories`, and `listings`, foreign keys, uniqueness constraints, price/enum checks, indexes, and seven category rows: Soccer, Basketball, Hockey, Tennis, Baseball, Fitness, Other. Flyway tracks applied migrations in `flyway_schema_history`. Add new versioned migrations for future changes instead of editing migrations already applied to a database.

`V2__normalize_user_emails.sql` lowercases and trims existing emails and adds a database normalization check while keeping V1's unique constraint. If old accounts normalize to the same email, migration fails rather than merging users or dropping constraints. Inspect and resolve those conflicts deliberately before retrying. Existing non-BCrypt placeholder password hashes are not valid login credentials; register a real account through the new endpoint.

Hibernate uses `ddl-auto=validate`: it checks mappings against the migrated schema and does not create or update tables. Open Session in View is disabled; DTO mapping happens within service transactions. Timestamp callbacks apply to JPA writes; direct SQL updates must explicitly update `updated_at`.

## API

The application listens on `http://localhost:8080`. The public read and authentication routes work without a token. Current-user routes and listing writes require a valid bearer JWT; changing an existing listing also requires ownership. All routes and HTTP methods outside the explicit allowlist are denied.

| Method | Path | Response |
| --- | --- | --- |
| GET | `/api/listings` | Public, server-filtered AVAILABLE listings in a pagination envelope; newest first by default |
| GET | `/api/listings/{id}` | One listing, including a sold listing; 404 if missing |
| GET | `/api/categories` | All categories alphabetically; public |
| POST | `/api/auth/register` | Public; validated registration, returns safe user DTO and HTTP 201 |
| POST | `/api/auth/login` | Public; returns access token, type, and lifetime |
| GET | `/api/users/me` | Authenticated; returns ID, full name, and email |
| POST | `/api/listings` | Authenticated; creates an AVAILABLE listing, returns 201 and Location |
| PUT | `/api/listings/{id}` | Owner only; replaces editable details, returns 200 |
| DELETE | `/api/listings/{id}` | Owner only; deletes listing, returns empty 204 |
| PATCH | `/api/listings/{id}/status` | Owner only; sets AVAILABLE or SOLD, returns 200 |
| GET | `/api/users/me/listings` | Authenticated; returns only own AVAILABLE and SOLD listings, newest first |

```powershell
curl.exe http://localhost:8080/api/listings
curl.exe http://localhost:8080/api/listings/1
curl.exe http://localhost:8080/api/categories
```

A new database has no users or listings, so `/api/listings` returns an empty page (`content: []`, `totalElements: 0`) and `/api/listings/1` returns 404. Only categories are seeded. Register and log in, then use the authenticated listing creation endpoint to add listings.

Example single-listing response from GET `/api/listings/{id}` when data exists:

```json
{
  "id": 1,
  "title": "Soccer ball",
  "description": "Lightly used ball",
  "price": 24.99,
  "condition": "GOOD",
  "status": "AVAILABLE",
  "category": { "id": 1, "name": "Soccer" },
  "seller": { "id": 1, "fullName": "Alex Seller" },
  "createdAt": "2026-11-08T12:00:00Z",
  "updatedAt": "2026-11-08T12:00:00Z"
}
```

Missing listings return HTTP 404 with `Content-Type: application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Listing not found",
  "status": 404,
  "detail": "Listing with ID 1 was not found.",
  "instance": "/api/listings/1"
}
```

Malformed IDs such as `/api/listings/abc` return structured HTTP 400 errors.

## Request flow

For `GET /api/listings`, Spring MVC routes the request to `ListingController`. It calls `ListingService`, which starts a read-only transaction and asks `ListingRepository` for available listings. Spring Data JPA/Hibernate executes SQL against PostgreSQL, fetching the category and seller relationships. The service maps only the selected page of entities to `ListingResponse` DTOs while the transaction is open and wraps them in `ListingPageResponse`. Spring MVC serializes the DTOs to JSON and returns HTTP 200. If an ID lookup finds no row, the service throws `ListingNotFoundException`, and `GlobalExceptionHandler` returns HTTP 404.

## Automated tests

```powershell
mvn test
```

Or use `./mvnw.cmd test` where the wrapper works. Tests activate the `test` profile and use an isolated H2 database in PostgreSQL compatibility mode. They run the real Flyway migration and Hibernate schema validation, then exercise the full Controller -> Service -> Repository stack through MockMvc. PostgreSQL and database environment variables are not required for tests.

The original seven tests are preserved and cover available-only filtering and private-field exclusion, lookup by ID, reading sold listings, structured missing-listing 404, malformed-ID 400, seeded category retrieval, and an empty listings response. Authentication tests additionally cover registration, normalization, duplicate emails, BCrypt verification, input limits (including UTF-8 bytes), login and generic credential failures, JWT claims and lifetime, public access, missing/invalid/tampered/expired tokens, wrong issuer, missing claims, deleted accounts, deny-by-default rules, and CORS. Focused service tests exercise the database uniqueness race path and distinguish other integrity failures. Test JWT keys are randomly generated for the test context, not production secrets. Fixture changes roll back after each integration test. H2 is a fast automated check, not a substitute for verifying the application against a local PostgreSQL instance.

If Maven defaults to an unwritable local repository, choose a writable cache:

```powershell
mvn "-Dmaven.repo.local=../.m2/repository" test
```


## Authentication classes and flows

- `RegisterRequest` and `LoginRequest`: bound and validate inputs. Both normalize emails by stripping outer whitespace and lowercasing with `Locale.ROOT`; passwords are never trimmed or normalized. Full names are trimmed. Request DTO `toString()` methods redact credentials.
- `PasswordByteLimit`: validates BCrypt's 72-byte UTF-8 maximum. Registration also requires 8-72 characters, a nonblank full name up to 255 characters, and a valid email up to 255 characters. Login accepts nonblank passwords up to the same byte limit; too-short incorrect passwords still get a generic 401. Invalid input receives 400 without echoing rejected values.
- `AuthController`: exposes POST registration and login and validates request DTOs.
- `AuthService`: checks normalized email availability, BCrypt-hashes the password, and saves a user transactionally. The unique database constraint remains the final guard when simultaneous requests pass the initial check. A unique violation is translated to 409, and the transaction rolls back. Registration returns `UserResponse`, not a JWT; log in separately.
- `RepositoryUserDetailsService`: loads the stored password hash using the submitted normalized email. It builds Spring Security `UserDetails` with the database user ID as its username, without adding roles.
- `AuthenticationManager` / `DaoAuthenticationProvider`: authenticate submitted email/password using that user-details service and the configured `PasswordEncoder`. Unknown accounts and incorrect passwords produce the same generic HTTP 401. The provider hides user-not-found exceptions and performs a dummy hash check for unknown users to reduce timing differences.
- `TokenService`: takes the authenticated principal's database ID and issues an HS256-signed access token with `sub`, `iss`, `iat`, and `exp`. `AuthResponse` contains `accessToken`, `tokenType: Bearer`, and `expiresIn: 3600` (seconds). Tokens contain no passwords, hashes, or email.
- `UserController` / `UserService`: read the validated `Jwt` principal's subject and fetch current user data. An account that no longer exists returns 401.
- `GlobalExceptionHandler`: handles application exceptions, invalid input, and login failures. `SecurityErrorHandler` handles filter-level 401/403 failures using the same Problem Detail structure. Error messages do not disclose token parsing/signature details.

### BCrypt verification

BCrypt is a one-way password hash, not encryption. During registration it generates a random salt and stores the algorithm marker, cost, salt, and resulting hash together. The default encoder cost is 10. During login, `PasswordEncoder.matches(submittedPassword, storedHash)` uses the stored salt and cost to check the password; it never decrypts the stored hash. The byte-limit validator rejects inputs above 72 UTF-8 bytes instead of allowing truncation or a hashing error. Equal passwords normally produce different stored hashes because their salts differ.

### JWT verification and the security filter chain

`SecurityFilterChain` defines which security filters run and which routes are allowed. On a protected request the built-in bearer authentication filter reads `Authorization: Bearer <token>`. `NimbusJwtDecoder` verifies the HS256 signature with the configured key. Its validators check issuer, expiration/not-before (zero clock skew), required issued-at/expiration claims, a nonfuture issued-at time, and a positive numeric user-ID subject. Missing, invalid, expired, and tampered tokens return 401.

After successful verification Spring stores a `JwtAuthenticationToken` in the request's security context. Its principal is the validated `Jwt`; its name is the token's `sub` (our database user ID). `@AuthenticationPrincipal Jwt jwt` injects that principal into `/api/users/me`, which loads the account by ID. A raw email or user ID sent separately by the caller is not trusted. Every bearer request is validated independently; no HTTP session is created or used.

CSRF is disabled because this API accepts authentication only through an explicitly sent bearer header and does not use session cookies, HTTP Basic, or form login. Browsers do not automatically attach our bearer header to a cross-site request. If cookie-based authentication is added later, revisit CSRF protection. CORS permits only exact configured origins and the GET/POST/PUT/PATCH/DELETE/OPTIONS methods with Authorization/Content-Type headers; credentials via cookies are disabled. CORS preflight requests are handled before authentication. CORS does not replace authentication or endpoint authorization.

Without authentication, requests requiring authorization return 401 with a bearer challenge. An authenticated request to a non-allowlisted route or method returns 403. A supplied invalid bearer token is rejected even on an otherwise public endpoint. CORS rejections use Spring's standard CORS rejection response; application authorization failures use Problem Detail JSON.

There are no refresh tokens, token revocation, logout endpoint, admin roles, or social login. Access tokens expire after 60 minutes. Discarding a token locally does not revoke it on the server.

## Manual Postman walkthrough

Start PostgreSQL and the application using the environment setup above. Set a Postman variable `baseUrl` to `http://localhost:8080`. Use a local/sensitive variable for `accessToken` and do not publish or sync tokens in shared collections.

1. **Register:** POST `{{baseUrl}}/api/auth/register`. Select Authorization -> No Auth, Body -> raw -> JSON, and send:

   ```json
   {
     "fullName": "Alex Seller",
     "email": "alex@example.com",
     "password": "LocalDemoPass123!"
   }
   ```

   Expect HTTP 201 with `id`, `fullName`, and `email`; no password fields. The example password is request input only and is not seeded or used as an application secret. Registering that email again (even with uppercase letters or outer spaces) returns 409. Invalid email, blank name, or a password shorter than eight characters returns 400.

2. **Login:** POST `{{baseUrl}}/api/auth/login`, No Auth, raw JSON:

   ```json
   {
     "email": "alex@example.com",
     "password": "LocalDemoPass123!"
   }
   ```

   Expect HTTP 200:

   ```json
   {
     "accessToken": "<signed JWT>",
     "tokenType": "Bearer",
     "expiresIn": 3600
   }
   ```

   Copy `accessToken` into your local Postman variable. An incorrect password or unknown valid-format email returns the same 401 error. Validation failures such as missing fields return 400.

3. **Current user:** GET `{{baseUrl}}/api/users/me`. Select Authorization -> Bearer Token and enter `{{accessToken}}` (without an extra `Bearer` prefix). Expect HTTP 200 with the registered user's ID, name, and email. Under No Auth, expect 401. Replace the token with `not-a-jwt` or alter a character in its signature to verify 401. A token used after its 60-minute expiration also returns 401; automated tests exercise expiration without waiting.

4. **Public routes:** GET `{{baseUrl}}/api/listings` and GET `{{baseUrl}}/api/categories` with No Auth. Expect 200. A fresh database has no listings, so an ID lookup returns the existing structured 404.

5. **Default denial:** GET `{{baseUrl}}/api/not-allowed` or POST `{{baseUrl}}/api/categories` using the valid bearer token. Expect 403. Without a token, a denied route returns 401.

The automated tests use real signed tokens and the full filter chain; they do not replace JWT verification with mocked authentication. Tests run with H2, so also use the steps above to verify your local PostgreSQL instance.

## Milestone 3: listing CRUD and ownership

No dependencies or schema changes were needed. Existing migrations remain unchanged. The existing category and seller foreign keys and listing indexes support the new operations.

### DTOs and validation

`CreateListingRequest` and `UpdateListingRequest` contain only `title`, `description`, `price`, `categoryId`, and `condition`. Both validate required fields. Titles are limited to 255 characters, descriptions to 10,000, category IDs must be positive, and price must be greater than zero with at most 10 integer digits and 2 decimal places (matching NUMERIC(12,2)). Monetary values remain BigDecimal; excessive decimal precision is rejected instead of silently rounded.

`UpdateListingStatusRequest` accepts only the required `status`. Conditions are NEW, LIKE_NEW, GOOD, FAIR, or POOR. Status is AVAILABLE or SOLD. Enum names are case-sensitive; invalid names and numeric ordinals return 400.

Seller identity is never bound from a request DTO. Extra JSON fields such as `sellerId` cannot override the JWT-derived seller. Likewise, create/PUT cannot set status: new listings start AVAILABLE, PUT preserves the current status, and status changes use the dedicated PATCH route. All responses reuse `ListingResponse`; no JPA entity or password hash is exposed.

### Service and entity methods

- `ListingService.createListing`: converts the validated JWT subject to a user ID, loads the existing seller and requested category, builds an AVAILABLE listing, and saves it. A token for a deleted account cannot create a listing (401).
- `ListingService.findOwnedListing`: shared by update, delete, and status change. It retrieves the listing (404 if absent), compares its seller ID with the JWT subject's user ID, and throws AccessDeniedException if they differ (403). The check is on the backend and cannot be bypassed by manipulating the request body.
- `ListingService.updateListing`: checks ownership first, looks up the category, and calls `Listing.updateDetails`. Seller, creation timestamp, and status are preserved.
- `ListingService.deleteListing`: checks ownership, then deletes the retrieved entity.
- `ListingService.updateListingStatus`: checks ownership and calls `Listing.changeStatus`. Either enum status can be set, including changing SOLD back to AVAILABLE.
- `ListingService.getMyListings`: filters by the JWT subject at the repository level. It includes both statuses and sorts newest first, breaking timestamp ties by descending ID. A user with no listings receives an empty array.
- `ListingRepository.findBySellerIdOrderByCreatedAtDescIdDesc`: fetches only the caller's listings together with category and seller data to avoid per-listing lazy-loading queries.
- `ListingController`: receives and validates create/update/status request DTOs, passes the validated principal's subject to the service, and returns the DTO plus the appropriate HTTP status. `UserController.getMyListings` delegates to the listing service.
- `CategoryNotFoundException`: signals a missing category and is translated to a structured 404. `GlobalExceptionHandler` also converts service-layer ownership failures into structured 403 responses.

Each write service method uses `@Transactional`; reads inherit the existing read-only transaction setting. Existing entities are managed by Hibernate, so updates use dirty checking. Explicit flushes before mapping update responses run the existing `@PreUpdate` callback, ensuring the returned `updatedAt` reflects the persisted timestamp. Repeating an identical update may produce no SQL update and leave `updatedAt` unchanged. No additional account lookup is needed for ownership comparisons: the validated subject is compared directly with the listing's stored seller ID.

SecurityConfig explicitly allows the five new authenticated routes while preserving public routes and deny-by-default behavior. JWT verification, stateless authentication, and the configured origin list remain in place; CORS now also permits PUT, PATCH, and DELETE.

### Request examples and status codes

All new endpoints require:

```http
Authorization: Bearer <accessToken>
```

For POST/PUT/PATCH also send `Content-Type: application/json`.

POST `/api/listings` and PUT `/api/listings/{id}` use the same payload shape. PUT requires all editable fields; it is not a partial update:

```json
{
  "title": "Soccer ball",
  "description": "Lightly used size 5 ball",
  "price": 24.99,
  "categoryId": 1,
  "condition": "GOOD"
}
```

Use a real category ID from GET `/api/categories` rather than assuming ID 1. POST returns HTTP 201 with a ListingResponse and a `Location: /api/listings/{id}` URL. PUT returns HTTP 200 with the updated ListingResponse.

PATCH `/api/listings/{id}/status`:

```json
{
  "status": "SOLD"
}
```

Returns HTTP 200 with the updated ListingResponse. Send `"AVAILABLE"` to make it available again. Public GET `/api/listings` excludes sold listings; public GET `/api/listings/{id}` and authenticated GET `/api/users/me/listings` still include sold listings.

DELETE `/api/listings/{id}` needs no body and returns HTTP 204 with no response body. The deleted listing subsequently returns 404 from public lookup.

| Situation | HTTP status |
| --- | --- |
| Created listing | 201 |
| Updated listing/status or retrieved own listings | 200 |
| Deleted listing | 204 |
| Invalid/missing fields, invalid enum, or malformed ID | 400 |
| Missing, invalid, or expired JWT | 401 |
| Valid JWT but caller does not own listing | 403 |
| Listing or selected category does not exist | 404 |

Validation errors include field names/messages, not rejected values. Ownership checks precede category lookup for an otherwise valid update. A valid authenticated mutation request for a missing listing receives 404; a request without valid authentication is rejected before reaching that lookup.

### Postman ownership walkthrough

Use the startup instructions above (`DB_USERNAME`, `DB_PASSWORD`, random Base64 `JWT_SECRET`, and optional `JWT_ISSUER`/`CORS_ALLOWED_ORIGINS`). Run `mvn spring-boot:run` from the Maven project folder. Keep tokens in local sensitive Postman variables.

1. Register two distinct accounts through POST `/api/auth/register`, then log each in through POST `/api/auth/login`. Save their tokens as `ownerToken` and `otherToken`. Use `baseUrl = http://localhost:8080`.
2. GET `{{baseUrl}}/api/categories` with No Auth and copy a category ID.
3. POST `{{baseUrl}}/api/listings` with Authorization -> Bearer Token -> `{{ownerToken}}`, raw JSON, and the creation payload above. Expect 201. Save the returned ID as `listingId`. The response seller ID should match the owner account, and status should be AVAILABLE.
4. PUT `{{baseUrl}}/api/listings/{{listingId}}` using ownerToken and all five editable fields (for example change price to 19.99). Expect 200, with the original seller and creation timestamp.
5. Repeat that PUT using otherToken. Expect 403. DELETE and PATCH status using otherToken must also return 403, and the listing must remain unchanged.
6. PATCH `{{baseUrl}}/api/listings/{{listingId}}/status` using ownerToken and `{"status":"SOLD"}`. Expect 200. Confirm it disappears from public GET `/api/listings` but is still returned by GET `/api/listings/{{listingId}}`.
7. GET `{{baseUrl}}/api/users/me/listings` using ownerToken. It includes the sold listing. Repeat with otherToken: that account must not see the owner's listings. This endpoint needs no user ID in the body or URL.
8. Try price 0 or negative, omit a required field, or send an invalid condition/status; expect 400. A positive category ID that does not exist returns 404. Remove the bearer token from any new route and expect 401.
9. DELETE `{{baseUrl}}/api/listings/{{listingId}}` with ownerToken. Expect 204 and no body. Repeat deletion or public lookup and expect 404.
10. Confirm an unsupported operation, such as PATCH `/api/listings/{{listingId}}` (without `/status`), remains 403 with a valid token.

Run `mvn test` (or the writable-cache command above). `ListingCrudTests` uses real signed JWTs and the full security/controller/service/repository stack. It checks persistence after clearing the JPA context, seller spoofing resistance, create/update validation, timestamp preservation, missing categories/listings, ownership failures for all three mutations, status visibility, user-scoped reads, and expanded CORS. Existing Milestone 1 tests are preserved; the earlier deny-by-default test now uses POST `/api/categories` because POST `/api/listings` is intentionally supported in Milestone 3. H2 remains test-only; use Postman to verify against your local PostgreSQL database as well.

## Milestone 4 frontend

The React + TypeScript + Vite app is a sibling of this Maven project at [`../frontend`](../frontend/README.md). Start this backend as described above, then run `npm.cmd ci` and `npm.cmd run dev` from that frontend folder. Open http://localhost:5173. The default development API proxy forwards `/api` requests to port 8080; this milestone does not change backend code or schema.

The frontend supports browsing/details, registration/login, create/edit, My Listings, status changes, and confirmed deletion. Access tokens stay in React memory and are cleared on logout/expiration or browser refresh. Read its README for environment configuration, testing, production hosting considerations, and step-by-step workflows.

## Milestone 5: server-side marketplace search

GET `/api/listings` remains public and always restricts results to AVAILABLE. All optional filters combine with AND. My Listings remains a separate, unchanged array API including AVAILABLE and SOLD; single-listing reads, CRUD, and JWT/ownership rules are unchanged.

**Compatibility change:** public GET `/api/listings` now returns a pagination envelope instead of a bare array. Read `content` for listing cards. The React API client and all existing catalogue tests were updated together. Restart the backend and frontend together after updating; an old running backend returning arrays is incompatible with the new frontend.

| Query parameter | Meaning | Default / validation |
| --- | --- | --- |
| `search` | Case-insensitive literal substring in title only | Optional; at most 100 characters; outer whitespace ignored; blank means no search |
| `categoryId` | Match category foreign key | Optional positive integer; nonexistent category returns 400 |
| `condition` | Match condition | Optional; NEW, LIKE_NEW, GOOD, FAIR, POOR; case-sensitive |
| `minPrice` | Inclusive minimum price | Optional nonnegative decimal |
| `maxPrice` | Inclusive maximum price | Optional nonnegative decimal; must be at least minPrice |
| `sort` | Explicit sort allowlist | `newest`, `oldest`, `price_asc`, `price_desc`; default newest |
| `page` | Zero-based page index | Default 0; must be nonnegative |
| `size` | Maximum results on one page | Default 12; range 1-50 |

Unsupported sort values, malformed numbers, invalid conditions/categories, reversed price ranges, and an excessively long search return HTTP 400 using the existing Problem Detail JSON format. No arbitrary entity property can be used as a sort field. A page whose offset exceeds JPA's integer offset limit also returns 400 rather than a database error.

```powershell
curl.exe "http://localhost:8080/api/listings?search=nike&categoryId=1&maxPrice=100&sort=price_asc&page=0&size=12"
curl.exe "http://localhost:8080/api/listings?condition=LIKE_NEW&minPrice=10&maxPrice=80&sort=oldest&page=0&size=2"
curl.exe "http://localhost:8080/api/listings?sort=price_desc&page=1&size=2"
```

Use a category ID returned by `/api/categories`. A response has this stable shape (counts below are illustrative):

```json
{
  "content": [],
  "page": 0,
  "size": 12,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

When matching data exists, `content` contains the existing ListingResponse objects; `totalElements` counts every match, not just this page. For 50 matches and size 12, totalPages is 5. A valid page beyond the last match returns empty content while preserving the actual totals; it does not silently clamp the requested page or return 404.

### How the query works

- `ListingSearchRequest` binds and validates query parameters; it supplies page/size/sort defaults.
- `ListingController` delegates to `ListingService.getAvailableListings(filters)` without implementing query logic.
- `ListingSpecifications.matching` constructs Criteria predicates for AVAILABLE, lowercased title substring, category, condition, and inclusive price bounds. `%`, `_`, and `!` in keyword input are escaped as literal characters, so user input is not treated as SQL wildcard syntax. Hibernate binds values as query parameters.
- The service validates the price range and category existence, maps sort names to fixed entity properties, and creates a `PageRequest`.
- `ListingRepository` extends `JpaSpecificationExecutor` and executes the specification with Pageable. Its to-one entity graph fetches seller and category in the page query, avoiding per-row lazy-loading queries without collection-fetch pagination problems.
- `ListingPageResponse` serializes only our DTO fields, not Spring's internal Page implementation.

The conceptual SQL is:

```sql
SELECT ...
FROM listings
WHERE status = 'AVAILABLE'
  AND LOWER(title) LIKE '%nike%' ESCAPE '!'
  AND category_id = 1
  AND price <= 100
ORDER BY price ASC, id ASC
LIMIT 12 OFFSET 0;
```

`Pageable` describes the requested page index, size, and ordering. `Page<Listing>` is the result: content plus page information and overall totals. Spring Data runs a count query when needed for total metadata; it can skip that query when the returned content already proves the total. Category validation adds one existence query only when a category filter is supplied.

All sorts include ID as a deterministic tie-breaker: newest uses createdAt DESC/id DESC, oldest uses both ASC, price_asc uses price ASC/id ASC, and price_desc uses both DESC. This prevents rows with identical prices/timestamps from having ambiguous ordering. Pages can still shift when other users create or delete listings between requests; pagination is not a frozen catalogue snapshot.

### Performance and migration

`V3__index_listing_price_sort.sql` adds `(status, price, id)`, supporting available-only price ranges and deterministic price ordering in forward or backward scans. The existing `(status, created_at DESC, id DESC)` index supports newest/oldest scans, and category/seller indexes remain useful. We do not add indexes for every filter combination: more indexes consume disk space and add work to inserts, price changes, status changes, and deletes. PostgreSQL chooses an execution plan based on selectivity and table statistics; an index is not a guarantee that every query will use it.

Only up to 50 listing entities are fetched/mapped per request; the default is 12. This reduces application memory, network transfer, and browser rendering compared with loading every listing. Counts still need database work, and deep OFFSET pages require scanning/skipping earlier matches. Very large datasets may eventually warrant keyset pagination.

A normal B-tree index does not efficiently accelerate `LOWER(title) LIKE '%term%'`; a lower(title) B-tree would not solve substring search. No such speculative index, extension, Redis, or external search engine was added. For a much larger catalogue, evaluate query plans with EXPLAIN ANALYZE and consider PostgreSQL trigram indexing if measurements justify its extra storage and write cost.

### Test search manually

1. Restart the backend so Flyway applies V3. Start/restart the frontend at localhost:5173.
2. Create several listings with varied titles, categories, conditions, and prices. Mark one SOLD and confirm it remains visible in My Listings but never appears in public search.
3. In Postman, GET `/api/listings` with No Auth; use the Params tab for filters. Search `nike` and `NIKE` against a matching title; expect the same matches.
4. Combine category, condition, and price range. Check all returned items satisfy every filter. Equal min/max matches the exact price.
5. Set size 2 and compare page 0, 1, and the final page. TotalElements stays consistent without data changes, content is limited to the page size, and ordering remains stable. Try the four sorts, including duplicate prices.
6. Try page -1, size 51, unsupported sort, negative prices, invalid condition/category, a 101-character keyword, and minPrice greater than maxPrice; expect structured 400 responses.
7. In the frontend, type a keyword (350ms debounce), choose category/condition and sort, enter prices and click Apply prices. Change pages and confirm filters remain selected. Changing criteria or page size resets page to 0. Clear filters restores defaults. The total matching count comes from the server.

`ListingSearchTests` covers all these query behaviors through MockMvc/JPA/Flyway with H2 in PostgreSQL mode. Existing authentication, CRUD, and ownership suites still run; the older timestamp assertion compares within one microsecond because database timestamp precision can differ from Java Instant nanoseconds. Frontend tests cover URL persistence, combined filter requests, pagination boundaries, page reset, debounce, stale-response rejection, price-range validation, and errors/retry. Live PostgreSQL testing remains useful for production-specific execution plans.

References: [Spring Data specifications and pagination](https://docs.spring.io/spring-data/jpa/reference/jpa/specifications.html), [PostgreSQL index trade-offs for substring matching](https://www.postgresql.org/docs/18/pgtrgm.html).

## Milestone 6: product image uploads

Owners can add up to five static JPEG, PNG, or WebP images per listing. Each uploaded file is limited to **5 MB (5,242,880 bytes)**. Multipart requests are capped at 6 MB and contain one file each; the frontend uploads selected files sequentially. Dimension limits are 6,000 pixels per side and 16 million pixels total to bound decompression memory. Animated PNG/WebP is not supported.

The validator ignores filename extensions and declared MIME types. It detects the image format from content, validates its container (including PNG chunk CRCs and WebP length), checks dimensions before decoding, rejects decoder errors/warnings, and decodes/re-encodes the pixels. Only JPEG/PNG/WebP inputs are allowed; SVG, GIF, scripts, text, empty, truncated, and corrupted files are rejected. Decoder support for WebP is provided by the small TwelveMonkeys `imageio-webp` dependency; no image server or cloud service is used.

Stored images are normalized: JPEG becomes a clean JPEG; PNG and WebP become PNG. Original metadata (including EXIF) and trailing uploaded content are not retained. WebP responses therefore correctly report `image/png`. Normalized output is also capped at 5 MB; a highly compressed source that expands past that cap must be reduced before upload. EXIF orientation is not applied automatically, so rotate photographs before uploading if needed.

### Storage configuration

The default local directory is `${user.home}/.sportmarket/uploads`, outside the application source tree. Optionally set an absolute path in the same terminal used to start Spring Boot:

```powershell
$env:IMAGE_STORAGE_DIR = Join-Path $env:USERPROFILE '.sportmarket/uploads'
mvn spring-boot:run
```

Use a dedicated directory writable only by the application account; do not point it at `src`, frontend public assets, or a web server's executable/static root. There is no static filesystem mapping. Files are read only through metadata-based image IDs, and no endpoint accepts storage paths. Server-generated UUID filenames are used with exclusive creation, strict key validation, path containment checks, and no-follow file reads; client filenames are never used as storage keys. Do not clear this directory on application restart. Back up the directory and PostgreSQL metadata together. This is local storage for one application installation, not shared cloud/multi-host storage.

`V4__create_listing_images.sql` creates ListingImage metadata: ID, listing foreign key, unique storage key, served content type, display order, and creation time. A unique (listing_id, display_order) constraint plus allowed slots 0-4 also enforces at most five images at database level. Deleting an image leaves slots stable; a later upload uses the first free slot. There is no reorder endpoint in this milestone.

### Endpoints

| Method | Path | Access | Success |
| --- | --- | --- | --- |
| POST | `/api/listings/{id}/images` | Authenticated listing owner | 201 and image metadata |
| GET | `/api/listings/{id}/images` | Public | 200, ordered metadata array |
| GET | `/api/listing-images/{imageId}/content` | Public | 200, validated image bytes |
| DELETE | `/api/listings/{listingId}/images/{imageId}` | Authenticated listing owner | 204, no body |

Upload with multipart/form-data and a **`file`** part. In Postman choose Body -> form-data, key `file`, change its type to File, and select the photo. Use Authorization -> Bearer Token for upload/delete. Let Postman/browser generate the Content-Type boundary; do not manually set a JSON content type.

Example metadata:

```json
{
  "id": 10,
  "listingId": 3,
  "contentType": "image/png",
  "displayOrder": 0,
  "createdAt": "2026-11-18T12:00:00Z",
  "url": "/api/listing-images/10/content"
}
```

Storage keys and absolute paths are not returned. Existing ListingResponse objects gain an additive `images` array using the same metadata; listings without photos have `images: []`. Public paginated listings and My Listings fetch all metadata for the returned listing IDs in one batch query, avoiding per-card metadata requests and avoiding collection fetch joins that could break pagination. Single-listing reads include its images too.

Content uses the normalized MIME type, an inline server-controlled filename, `X-Content-Type-Options: nosniff`, `Content-Security-Policy: default-src 'none'; sandbox`, and Cache-Control no-store. Public embedding is allowed with Cross-Origin-Resource-Policy cross-origin, so a separately hosted frontend can show the public photos. Uploads are never executed.

Errors use Problem Detail JSON: 400 for invalid/corrupted/unsupported images, bad dimensions, or exceeding five images; 401 for missing/invalid authentication; 403 for non-owners; 404 for absent listings/images or mismatched listing/image IDs; 413 for file/request/output size limits. Read/upload storage failures return a generic 500 without filesystem details; post-commit cleanup failures are logged because metadata deletion has already committed. Security still denies unspecified routes.

### Architecture and consistency

- `ListingImageController` handles multipart binding, public content headers, and HTTP statuses.
- `ListingImageService` checks the validated JWT subject against the stored seller ID and controls the upload/delete business transaction.
- `ListingImageRepository` stores metadata. `ListingImageResponse` contains safe public fields only.
- `ImageValidator` checks actual bytes and normalizes raster pixels with ImageIO.
- `ImageStorageService` abstracts store/read/delete. `LocalImageStorageService` implements a single safe local storage root.
- `ImageFileLifecycle` coordinates file effects with transaction completion.

Uploads/deletions lock the parent listing row. Listing updates/status/deletion also use this parent lock, so concurrent mutations cannot race an image upload past the five-image limit or create metadata for a deleted listing. The five-slot database constraint is an additional guard.

Upload sequence: validate -> write a new uniquely named file -> save metadata -> commit. If the transaction rolls back, its new file is deleted. Image deletion removes metadata in a transaction and deletes its file only after commit, preserving the original file on rollback. Listing deletion explicitly removes managed image metadata before deleting the listing and schedules every corresponding file for deletion after commit; the foreign-key cascade is an additional database safety net.

PostgreSQL and the local filesystem are not a single atomic transaction. Cleanup failures are logged with a generated storage key, and a process crash between file creation and metadata commit, or between deletion commit and filesystem cleanup, can leave unreferenced files. No scheduler/outbox was added. If that occurs, stop uploads, reconcile directory keys with listing_images.storage_key, and remove only verified unreferenced files. Public reads require metadata, so orphan files cannot be fetched through the API. Direct manual SQL deletion bypasses application file cleanup; use the API for normal deletion.

### Manual and automated testing

Restart the backend to apply V4 and restart the frontend. Create a listing with photos, inspect its card/gallery, edit it to add/remove photos, and verify a second account cannot modify them. Try a renamed text file, a corrupted image, a file over 5 MB, and a sixth photo. Deleting an image should remove its metadata and file; deleting its listing should remove all associated files. Public GET metadata/content needs no token.

`ListingImageTests` uses real files in an isolated temporary directory and a separate H2 database. It covers PNG/JPEG/WebP, corrupted/unsafe formats, real servlet multipart limits, max five under concurrent uploads, ownership, public content/security headers, deletion cleanup, parent deletion, rollback behavior, mismatched image IDs, missing resources, and traversal attempts. Tests never use the configured production upload directory. Run the full backend suite with `mvn test` as before.

References: [TwelveMonkeys supported formats](https://github.com/haraldk/TwelveMonkeys), [Spring Boot multipart limits](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/servlet/autoconfigure/MultipartProperties.html).
