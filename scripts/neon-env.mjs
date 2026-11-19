import { parseEnv } from 'node:util';
import { readFileSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { randomBytes } from 'node:crypto';

/** Convert standard Postgres URLs to JDBC without putting credentials in logs/arguments. */
export function jdbcConnection(value) {
  let url;
  try { url = new URL(value); } catch { throw new Error('A valid Neon PostgreSQL connection URL is required.'); }
  if (!['postgres:', 'postgresql:'].includes(url.protocol) || !url.hostname || !url.username || !url.password || url.pathname.length < 2) {
    throw new Error('Neon connection URL must include a host, database, username, and password.');
  }
  if (!url.searchParams.has('sslmode')) url.searchParams.set('sslmode', 'require');
  if (['disable', 'allow', 'prefer'].includes(url.searchParams.get('sslmode'))) {
    throw new Error('Neon connections must require TLS.');
  }
  // libpq and pgJDBC use different parameter names for channel binding.
  if (url.searchParams.has('channel_binding')) {
    url.searchParams.set('channelBinding', url.searchParams.get('channel_binding'));
    url.searchParams.delete('channel_binding');
  }
  return {
    url: `jdbc:postgresql://${url.host}${url.pathname}?${url.searchParams}`,
    username: decodeURIComponent(url.username),
    password: decodeURIComponent(url.password),
  };
}

export function neonEnvironment(root, inherited = process.env) {
  const path = inherited.NEON_ENV_FILE
    ? resolve(root, inherited.NEON_ENV_FILE)
    : resolve(root, existsSync(resolve(root, '.env')) ? '.env' : '.env.local');
  if (!existsSync(path)) throw new Error('Neon environment file is missing. Run neon deploy first.');
  const values = { ...parseEnv(readFileSync(path, 'utf8')), ...inherited };
  const pooled = jdbcConnection(values.DATABASE_URL);
  if (!values.DATABASE_URL_UNPOOLED) throw new Error('DATABASE_URL_UNPOOLED is required for Flyway. Run neon env pull.');
  const direct = jdbcConnection(values.DATABASE_URL_UNPOOLED);
  if (new URL(values.DATABASE_URL_UNPOOLED).hostname.includes('-pooler.')) {
    throw new Error('Flyway requires the direct, non-pooled Neon endpoint.');
  }
  return {
    ...values,
    SPRING_PROFILES_ACTIVE: 'neon',
    DB_URL: pooled.url,
    DB_USERNAME: pooled.username,
    DB_PASSWORD: pooled.password,
    DB_MIGRATION_URL: direct.url,
    DB_MIGRATION_USERNAME: direct.username,
    DB_MIGRATION_PASSWORD: direct.password,
    JWT_SECRET: values.JWT_SECRET || randomBytes(32).toString('base64'),
  };
}
