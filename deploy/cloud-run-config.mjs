// Writes one private Secret Manager payload; never prints credentials.
import { readFileSync, writeFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { jdbcConnection, neonEnvironment } from '../scripts/neon-env.mjs';

try {
  const directory = process.env.SPORTMARKET_SECRET_DIRECTORY;
  if (!directory) throw new Error('Private configuration directory is required.');
  let pooled, direct;
  if (process.env.SPORTMARKET_USE_ENV_FILE === 'true') {
    const env = neonEnvironment(process.cwd());
    pooled = { url: env.DB_URL, username: env.DB_USERNAME, password: env.DB_PASSWORD };
    direct = { url: env.DB_MIGRATION_URL, username: env.DB_MIGRATION_USERNAME, password: env.DB_MIGRATION_PASSWORD };
  } else {
    pooled = jdbcConnection(process.env.SPORTMARKET_NEON_POOLED);
    direct = jdbcConnection(process.env.SPORTMARKET_NEON_DIRECT);
    if (new URL(process.env.SPORTMARKET_NEON_DIRECT).hostname.includes('-pooler.')) {
      throw new Error('The second connection string must have connection pooling switched OFF.');
    }
  }
  // Rotating the database configuration must not unexpectedly invalidate JWTs.
  let previous = {};
  try { previous = JSON.parse(readFileSync(directory + '/current.json', 'utf8')); }
  catch (error) { if (error.code !== 'ENOENT') throw new Error('Existing private configuration could not be read.'); }
  const config = {
    'spring.datasource.url': pooled.url,
    'spring.datasource.username': pooled.username,
    'spring.datasource.password': pooled.password,
    'spring.flyway.url': direct.url,
    'spring.flyway.user': direct.username,
    'spring.flyway.password': direct.password,
    'app.jwt.secret': previous['app.jwt.secret'] || randomBytes(32).toString('base64'),
  };
  writeFileSync(directory + '/application.json', JSON.stringify(config), { mode: 0o600 });
} catch (error) { console.error(error.message); process.exitCode = 1; }
