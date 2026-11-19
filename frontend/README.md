# SportMarket frontend

Milestones 4-6: a responsive React + TypeScript marketplace built with Vite, React Router, Tailwind CSS, and the browser Fetch API. It connects to the existing Spring Boot backend; it does not use mock listings in the application or add backend endpoints.

## Run locally

Use Node.js 22.12+ or Node.js 24 LTS, npm, the existing Java backend, and PostgreSQL. On Windows, use `npm.cmd` if PowerShell blocks `npm.ps1`; no execution-policy change is required.

From the workspace root, in a backend terminal:

```powershell
cd sportmarket
# Set DB_USERNAME, DB_PASSWORD, and JWT_SECRET in this terminal using the backend README.
mvn spring-boot:run
```

Keep that terminal running. In a second terminal, from the workspace root:

```powershell
cd frontend
npm.cmd ci
# Optional: copy the documented defaults for customization.
Copy-Item .env.example .env.local
npm.cmd run dev
```

Open http://localhost:5173. The backend must run on http://localhost:8080 by default. The Vite port is fixed so another application does not silently change it. If it is occupied, stop that process before starting Vite.

## Configuration and API integration

| Setting | Default | Purpose |
| --- | --- | --- |
| `VITE_API_URL` | `/api` | API base URL used in the browser; includes `/api` and has no trailing slash requirement |
| `API_PROXY_TARGET` | `http://localhost:8080` | Backend target for Vite's local `/api` proxy |

The default relative URL lets the browser call `/api` on the frontend origin. Vite forwards those requests to Spring Boot, including the Authorization header. For browser requests, the local proxy also rewrites Origin to match the backend target; rewriting Host alone would cause Spring to reject forwarded POST requests as cross-origin. This applies only to the local Vite dev/preview servers, not direct API requests or backend security rules. This avoids needing changes to the backend CORS configuration for local development. These variables are URLs, not secrets. Never put DB_PASSWORD, JWT_SECRET, access tokens, or other secrets in VITE_* variables: Vite bundles those values into public JavaScript. Restart Vite after changing environment files.

For a separately hosted API, set `VITE_API_URL=https://your-api.example/api` before building. Configure the backend's existing `CORS_ALLOWED_ORIGINS` environment variable with the exact frontend origin (scheme, hostname, port; no path). Keep backend authorization in place. A production host also needs SPA fallback to `index.html` for React Router paths, and an `/api` reverse proxy if using the relative base. Vite development proxy configuration is not a production API deployment.

`src/api/client.ts` is the only Fetch boundary. It maps backend DTOs, sends JSON content types, attaches bearer headers only to protected requests, supports abortable reads, handles 204 deletes without JSON parsing, and preserves Problem Detail status/field errors. Public reads never attach stale credentials. Network failures, 400 validation, 401 expiration, 403 ownership failures, 404 missing listings, and 409 duplicate email are handled in the UI.

## Folder structure

```text
frontend/
  public/favicon.svg       Local brand asset; no remote asset dependency
  src/
    api/client.ts          Typed API methods and error conversion
    auth/AuthContext.tsx   In-memory session, login/logout, expiry, route guard
    components/            Navigation, cards, form fields, listing form, feedback/dialog
    hooks/useResource.ts   Loading/error/retry state and request cancellation
    lib/format.ts          Price, condition, and date display
    pages/                 Home, details, auth, listing editor, My Listings
    test/                  API, authentication, and workflow tests
    types.ts               Interfaces matching backend DTOs and enum strings
    App.tsx                Client-side routes
    main.tsx               Provider/router bootstrap
    styles.css             Tailwind import, design tokens, responsive styling
  .env.example             Public configuration template
  vite.config.ts           React/Tailwind plugins, dev proxy, test setup
```

## Authentication state

1. Login POSTs email/password to `/api/auth/login` without a bearer token.
2. The returned token is used to GET `/api/users/me`. The backend-verified identity determines the current user; the UI does not trust unverified token claims for identity.
3. The token, user, and expiration deadline live only in the AuthProvider's React state/ref. Nothing is written to localStorage, sessionStorage, cookies, URLs, or a persistence library.
4. `authorized()` supplies the current token to protected API methods. A 401 clears token/user state and displays a login message. A timer and focus/visibility checks also clear expired sessions based on `expiresIn`.
5. Protected routes redirect to login and preserve an internal destination so users can continue after logging in. A 403 displays an ownership error; it does not log out an otherwise valid user.
6. Logout immediately clears token and identity. Reloading the browser or opening a new tab also starts unauthenticated. This is intentional memory-only behavior, not a bug.

UI route guards and owner-only controls improve navigation; they do not replace backend JWT verification or ownership checks. There is no refresh-token flow or server revocation in this milestone. Public pages remain usable while signed out.

## Complete user workflows

### Browse and inspect

Visit `/` without logging in. Available listings come from GET `/api/listings`, categories from GET `/api/categories`. Keyword search, category/condition/price filtering, ordering, and pagination are performed by the backend. The frontend sends the selected query parameters and renders only the returned page. Click a card to open `/listings/:id`, showing all public fields and the seller name. Sold listings can still be opened by direct link. Empty, loading, missing, and retry states are supported.

Listings use their first uploaded photo as a cover, with the neutral local equipment illustration when there is no image or an image cannot load. Prices use the existing dollar-symbol convention. The API has no currency field, so the frontend does not claim a specific CAD/USD currency.

### Register and log in

Open `/register`, enter name/email/password, and submit. Validation messages appear beside fields; duplicate email errors are shown. Registration calls POST `/api/auth/register` and redirects to `/login` with a success message. Login uses the registered credentials. Successful login opens My Listings or the previously requested protected page. Passwords are not trimmed; registration checks the backend's BCrypt 72-byte UTF-8 limit as well as character constraints.

### Create and edit

Choose Create listing while signed in. Enter title, description, price greater than zero with at most two decimals, a real category, and a condition. POST `/api/listings` sends only those editable fields. The backend assigns seller from its validated JWT subject and status AVAILABLE. On success, the app opens listing details with a success notice.

Owners see Edit listing on their detail page and dashboard. `/listings/:id/edit` loads current details and prepopulates the reusable form. PUT sends all five editable fields. It cannot change seller, creation timestamp, or status. Client-side owner checks hide the form for other users; backend 403 responses are also handled in case permissions change or the server rejects the request.

### Manage your listings

`/my-listings` calls GET `/api/users/me/listings`. All/Available/Sold filters show only the returned current-user listings. Mark as sold / Make available PATCHes `/api/listings/:id/status`. Status changes refresh the dashboard and show confirmation. Public browsing still excludes sold listings.

Delete opens an accessible native confirmation dialog. Keep listing or Escape cancels it; no delete request is made. Confirming DELETE returns 204, removes the listing after reloading the dashboard, and displays success. Controls disable while a write is pending. Errors leave the user informed without silently pretending the change succeeded.

Use two accounts to verify ownership: create a listing under the first account, log out, and log in as the second. The first account's listing must not appear in My Listings, and its edit URL must show an owner-only message. The Spring Boot service remains responsible for rejecting direct unauthorized writes.

## Build and tests

```powershell
npm.cmd test
npm.cmd run build
```

Tests use Vitest, Testing Library, and jsdom with mocked HTTP responses. They cover bearer headers and payloads, public reads, 204 handling, server validation, network errors, memory-only auth, logout, provider remount, timed expiration, protected-request 401s, route guards, browsing/search/details, duplicate registration, protected creation, prefilled edits and 403 handling, status changes, and delete confirmation/cancel. They do not depend on your live database.

The production build is emitted to `dist/`. For a local production-build preview:

```powershell
npm.cmd run preview
```

Keep the backend running. The preview server uses the same API proxy target. Production deployment itself is outside this local frontend milestone; configure your real host as described above.

## Manual acceptance checklist

- Start both apps and browse categories/listings; check a narrow mobile viewport and navigation menu.
- Register, see the login success notice, then log in and create a listing.
- Confirm the new listing appears publicly and in My Listings.
- Edit it, mark it sold, reopen its details, and make it available again.
- Cancel deletion once, then confirm it and verify it disappears.
- Verify missing/invalid tokens go to login and non-owner changes remain forbidden.
- Refresh the browser: you should be logged out while public browsing still works.

No payments, messaging, favorites, caching infrastructure, or later milestones are included.

## Milestone 5: paginated catalogue

The public catalogue API now returns `ListingPage` with `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, and `last`. `src/types.ts` defines that contract, and `api.listings(filters, signal)` serializes supported filter fields with URLSearchParams. My Listings still uses its original array response; sold listings remain included there.

The home page supports:

- Title keyword search, debounced for 350ms; matching is case-insensitive on the server and limited to 100 input characters.
- Category dropdown fetched independently from `/api/categories`, without re-fetching it on each page/filter change.
- Condition dropdown with the backend's exact enum values.
- Nonnegative minimum/maximum prices. Click **Apply prices** to commit both bounds together; reversed ranges show a useful message without making a request.
- Newest, oldest, price ascending, and price descending sorting.
- Previous/Next controls, current/total pages, and 12/24/50 results-per-page choices.
- The total matching count and current range, loading skeletons, no matches, out-of-range pages, server validation errors, and retry controls.

Selected filters, sorting, size, and zero-based page live in the URL query string. This preserves them across pagination and browser history, supports bookmarks, and makes a refresh reproduce the public search. Search/filter/sort/size changes reset page to 0; Previous/Next changes only page. Search typing replaces the current history entry after debounce, while explicit filter/page changes can be navigated with browser Back/Forward.

`useResource` aborts obsolete requests and checks the AbortSignal before applying their results or errors. Even if an old request finishes late, it cannot replace the current filter results. The frontend does not download or filter the whole marketplace in memory.

**Restart both backend and frontend after updating.** The old bare-array public listing response is incompatible with the new page envelope. No other CRUD, auth, or current-user API contract changes.

Example bookmark:

```text
http://localhost:5173/?search=nike&categoryId=1&condition=GOOD&minPrice=10&maxPrice=100&sort=price_asc&page=0&size=12
```

Example API request:

```text
http://localhost:8080/api/listings?search=nike&categoryId=1&maxPrice=100&sort=price_asc&page=0&size=12
```

Use category IDs that actually exist. All filters are optional; the backend defaults to page 0, size 12, and newest ordering. Invalid filter inputs return 400. Public browsing still needs no token, and all existing protected actions retain the in-memory auth flow and backend ownership checks.

`src/test/search.test.tsx` covers combined filter persistence across pages, defaults, page reset, disabled final-page navigation, keyword debounce, atomic price application, clearing filters, outdated responses, and error recovery. Existing browsing and API mocks were updated to the new response shape, while My Listings mocks remain arrays. See the backend README for validation details, SQL translation, index rationale, and Postman checks.

## Milestone 6: photos

Create and edit forms include an Add photos control with previews, removal of queued selections, and owner-only deletion of stored images. JPEG/PNG/static WebP are accepted, up to five photos and 5 MB each. These client checks are convenience only: the backend validates actual content and limits again. Preview object URLs are revoked when selections change or the control unmounts.

On save, the app first creates/updates listing details, then uploads each selected file to POST `/api/listings/{id}/images` in its own multipart request. The API client passes FormData directly and deliberately does not set Content-Type: the browser supplies the multipart boundary. Bearer authentication is attached normally.

If a photo upload fails, the form explicitly reports that the listing was saved and how many selected photos uploaded. It keeps the saved listing ID, retains successful metadata, and leaves only failed/not-yet-uploaded selections queued. Retrying updates that same listing rather than creating another one. Invalid photos can be removed before retrying. If authentication expires mid-upload, the user returns to login with a partial-success notice and the saved listing's edit URL. A user who leaves during partial failure can find the saved listing in My Listings and finish editing it later.

Deleting an existing photo happens immediately through DELETE `/api/listings/{listingId}/images/{imageId}`. Canceling the surrounding listing form does not restore a photo already deleted; the control explains this. Controls show upload/delete errors and disable conflicting actions while pending. Selected-but-not-saved files stay only in component memory, not localStorage or IndexedDB.

Listing cards and dashboard thumbnails show the first image returned in display order. Detail pages show a main photo and thumbnail gallery. Broken images fall back to the existing placeholder. Public image content URLs use the configured API base URL; no token or secret appears in an image URL. Public listing payloads include an additive `images` array so cards do not need one metadata request per listing.

The backend normalizes JPEG to JPEG and PNG/WebP to PNG, strips source metadata, and rejects corrupt images, animation, excessive dimensions, or oversized normalized output. See the backend README for storage paths, security headers, limits, transaction/file cleanup, and Postman examples. No frontend storage service, image CDN, or cloud upload credentials are introduced.

To test in the UI: create with two photos, open its card and gallery, edit to remove one and add another, then delete the listing. Also test more than five files, an oversized file, and a non-owner account. A renamed invalid image should be rejected by the backend; the saved listing remains clearly identified so you can correct its photos. `src/test/images.test.tsx` covers multipart boundaries, selection validation, preview cleanup, cover fallback, gallery selection, existing-image removal, and partial-failure retry without duplicate creation.
