#!/usr/bin/env bash
# Run from the repository root in Google Cloud Shell. Uses billable Google Cloud services.
set +x
set -euo pipefail
umask 077

project_id="${1:?Usage: bash deploy/cloud-run.sh PROJECT_ID}"
region="${SPORTMARKET_REGION:-northamerica-northeast2}"
service_name=sportmarket
runtime_account="sportmarket-run@${project_id}.iam.gserviceaccount.com"
bucket_name="${project_id}-sportmarket-photos"
secret_name=sportmarket-config
image="${region}-docker.pkg.dev/${project_id}/sportmarket/app:$(git rev-parse --short HEAD)"

[[ -f Dockerfile && -f neon.ts ]] || { echo 'Run from the SportMarket repository root.' >&2; exit 1; }
command -v gcloud >/dev/null || { echo 'Run this in Google Cloud Shell.' >&2; exit 1; }
node -e 'const [major,minor]=process.versions.node.split(".").map(Number); if(major<22 || (major===22 && minor<12))process.exit(1)' \
  || { echo 'Node 22.12+ is required. In Cloud Shell, run nvm install 24 and retry.' >&2; exit 1; }

echo "Deploying ${service_name} in project ${project_id}, region ${region}."
echo 'Creates/updates SportMarket-only Cloud Run, build, registry, private photo bucket, secret, and runtime identity.'
echo 'Google Cloud usage charges may apply. No Cloud SQL instance will be created.'

secret_directory="$(mktemp -d -t sportmarket-config.XXXXXXXX)"
cleanup() {
  rm -f -- "${secret_directory}/current.json" "${secret_directory}/application.json"
  rmdir -- "$secret_directory"
}
trap cleanup EXIT
export SPORTMARKET_SECRET_DIRECTORY="$secret_directory"
if [[ -f .env || -f .env.local ]]; then
  export SPORTMARKET_USE_ENV_FILE=true
else
  echo 'In Neon Connect, copy the connection STRING (not the psql command).'
  read_connection() {
    if ! IFS= read -r -s -p "$1" "$2" </dev/tty; then
      echo >&2
      echo 'Input closed. Use the Cloud Shell editor to save DATABASE_URL and DATABASE_URL_UNPOOLED in private .env.local, then rerun.' >&2
      return 1
    fi
    echo
    if [[ -z "${!2}" ]]; then
      echo 'No connection string received. Stopped; do not paste credentials at the shell command prompt.' >&2
      return 1
    fi
  }
  read_connection 'Paste connection string with pooling ON (hidden): ' SPORTMARKET_NEON_POOLED
  read_connection 'Paste connection string with pooling OFF (hidden): ' SPORTMARKET_NEON_DIRECT
  export SPORTMARKET_NEON_POOLED SPORTMARKET_NEON_DIRECT
fi

gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com \
  secretmanager.googleapis.com storage.googleapis.com --project="$project_id" --quiet

if gcloud secrets describe "$secret_name" --project="$project_id" >/dev/null 2>&1; then
  gcloud secrets versions access latest --secret="$secret_name" --project="$project_id" \
    --out-file="${secret_directory}/current.json"
fi
node deploy/cloud-run-config.mjs
unset SPORTMARKET_NEON_POOLED SPORTMARKET_NEON_DIRECT

if ! gcloud iam service-accounts describe "$runtime_account" --project="$project_id" >/dev/null 2>&1; then
  gcloud iam service-accounts create sportmarket-run --display-name='SportMarket Cloud Run' --project="$project_id"
fi
if ! gcloud artifacts repositories describe sportmarket --location="$region" --project="$project_id" >/dev/null 2>&1; then
  gcloud artifacts repositories create sportmarket --repository-format=docker --location="$region" --project="$project_id"
fi
if ! gcloud storage buckets describe "gs://${bucket_name}" --project="$project_id" >/dev/null 2>&1; then
  gcloud storage buckets create "gs://${bucket_name}" --location="$region" --uniform-bucket-level-access \
    --public-access-prevention --project="$project_id"
fi
gcloud storage buckets add-iam-policy-binding "gs://${bucket_name}" \
  --member="serviceAccount:${runtime_account}" --role=roles/storage.objectUser --project="$project_id" >/dev/null

if ! gcloud secrets describe "$secret_name" --project="$project_id" >/dev/null 2>&1; then
  gcloud secrets create "$secret_name" --replication-policy=automatic --project="$project_id"
fi
secret_version="$(gcloud secrets versions add "$secret_name" --data-file="${secret_directory}/application.json" \
  --project="$project_id" --format='value(name)')"
secret_version="${secret_version##*/}"
gcloud secrets add-iam-policy-binding "$secret_name" --project="$project_id" \
  --member="serviceAccount:${runtime_account}" --role=roles/secretmanager.secretAccessor >/dev/null

# Only source files are uploaded; .gcloudignore excludes local secrets, caches, and uploads.
gcloud builds submit . --tag="$image" --region="$region" --project="$project_id" --timeout=1800s
gcloud run deploy "$service_name" --image="$image" --region="$region" --project="$project_id" \
  --service-account="$runtime_account" --execution-environment=gen2 --allow-unauthenticated \
  --cpu=1 --memory=1Gi --concurrency=2 --min-instances=0 --max-instances=1 --timeout=60s --port=8080 \
  --set-env-vars='^@^SPRING_PROFILES_ACTIVE=production,neon@IMAGE_STORAGE_DIR=/var/data/uploads' \
  --set-secrets="SPRING_APPLICATION_JSON=${secret_name}:${secret_version}" \
  --add-volume="name=listing-photos,type=cloud-storage,bucket=${bucket_name},mount-options=uid=10001;gid=10001;file-mode=600;dir-mode=700" \
  --add-volume-mount=volume=listing-photos,mount-path=/var/data \
  --startup-probe='httpGet.path=/api/categories,httpGet.port=8080,periodSeconds=10,timeoutSeconds=5,failureThreshold=24' \
  --quiet

service_url="$(gcloud run services describe "$service_name" --region="$region" --project="$project_id" --format='value(status.url)')"
curl --fail --silent --show-error --retry 3 --retry-all-errors "${service_url}/api/categories"
curl --fail --silent --show-error --output /dev/null "${service_url}/"
printf '\nSportMarket URL: %s\n' "$service_url"
echo 'Next: verify registration/login, listing creation, photo upload, ownership, and photo persistence after a redeploy.'
