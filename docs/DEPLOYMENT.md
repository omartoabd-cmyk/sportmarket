# Deploy SportMarket

## Architecture

The root Dockerfile builds React and packages its output in the existing Spring Boot jar. One web service serves the UI and `/api` on the same HTTPS origin. It needs no Vite development server or cross-origin browser configuration. The `production` profile forwards known React page routes to `index.html`; API access remains deny-by-default.

PostgreSQL stores users, listings, and photo metadata. A persistent disk stores photo bytes under `/var/data/uploads`. Local development keeps its two-process workflow. No local database or private uploads are copied into the image.

## Estimated costs — review before provisioning

The root `render.yaml` specifies paid resources in Ohio:

| Resource | Configuration | Approximate monthly USD |
| --- | --- | --- |
| Web service | `0.5c-512mb`, 512 MB RAM | $7 |
| PostgreSQL | `0.1c-256mb`, 256 MB RAM | $6 |
| Photo disk | 1 GB | $0.25 |
| Database storage | 1 GB | $0.30 |

Baseline estimate: **$13.55/month**, plus usage, taxes, and any workspace charges. Checked November 19, 2026; review the current dashboard estimate before applying. Preparing files creates no paid resources. The smallest JVM service may need more memory under load, which changes its price.

References: [pricing](https://render.com/pricing), [Blueprint fields](https://render.com/docs/blueprint-spec), [persistent disks](https://render.com/docs/disks). Free web services cannot attach persistent disks, and free PostgreSQL expires after 30 days; see [free-service limits](https://render.com/docs/free).

## Publish

1. Push the project root, containing both applications, to GitHub. Wait for CI, including the Docker build, to pass.
2. Connect GitHub to Render. Choose **New → Blueprint**, select the repository, and use root `render.yaml`.
3. Review service plans, disk sizes, region, and the current total cost. Applying the Blueprint creates billable resources.
4. Render injects database credentials and generates a stable Base64 JWT secret. Never copy secrets to GitHub, screenshots, or documentation.
5. Wait for Flyway and startup. The health check uses `/api/categories`, which also exercises database access. Copy the actual healthy service's HTTPS URL.
6. Complete the smoke tests below before sharing that URL.

The entrypoint converts Render's `DATABASE_URL` into a JDBC address; username/password are supplied separately. Raw `postgresql://` URLs are not JDBC URLs. Outside Render, provide `DB_URL=jdbc:postgresql://host:5432/database`, `DB_USERNAME`, and `DB_PASSWORD` separately.

Database external access is disabled; the web service uses the private network. Keep `JWT_SECRET` stable across deploys. `VITE_API_URL` is `/api` during the container build. Render supplies `PORT`, and the production profile binds on `0.0.0.0`.

## Fictional catalogue (optional)

Use Python 3 from the project root **only against an empty, disposable demo deployment**:

```powershell
python scripts/seed-demo.py https://YOUR-ACTUAL-SERVICE.onrender.com --confirm-empty-demo-target
```

The script creates 14 fictional listings across all seven categories, enough for pagination. It generates original PNG illustrations and uploads them through the real API. It never reads your PostgreSQL database or existing photos.

It refuses targets with available listings. A fixed marker email prevents repeating a successful or partial seed. The marker account's random password is never saved or printed; visitors create their own accounts to test listing management. If a request fails, completed records remain. Inspect the failure rather than repeatedly rerunning; reset only a deliberately disposable demo database if a fresh start is needed.

These illustrations are not photographs of real products. Script checks: `python -m unittest discover -s scripts -p 'test_*.py'`.

## Smoke tests

- Open the HTTPS root and a listing URL directly in a new tab. Confirm JS/CSS, gallery photos, search, filters, and page two load.
- Register a fictional user, log in, create a listing with a small photo, edit, mark sold, and delete.
- Use another account to verify owner-only mutations return 403. Public browsing must remain accessible signed out.
- Confirm missing listings return 404, invalid fields return 400, and oversized uploads return 413.
- Restart/redeploy and verify listings **and photo bytes** survive. If storage startup fails, check that the mounted directory is writable by UID 10001.
- Check browser/service errors without publishing passwords, JWTs, or request bodies.

## Docker locally

Start Docker Desktop, then run `docker build -t sportmarket .` from the root.

Create a private ignored `.env` with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `JWT_SECRET`. If PostgreSQL runs on your Windows host, the container JDBC host is usually `host.docker.internal`, not `localhost`.

```powershell
docker run --rm --name sportmarket-demo -p 8080:8080 --env-file .env --mount source=sportmarket-photos,target=/var/data sportmarket
```

Use a disposable database for deployment checks. The JVM runs as UID 10001; mounted storage must be writable by that user. Automated backend tests use H2; verify PostgreSQL migration/startup separately before publishing.

## Operations and limits

Keep one web instance with local photo storage. Persistent-disk deploys can involve downtime. Database backups and photo snapshots are separate; restoring only one may leave inconsistent metadata/files. Monitor storage, traffic, and billing. Total per-user upload quotas and registration rate limiting are not implemented yet.
