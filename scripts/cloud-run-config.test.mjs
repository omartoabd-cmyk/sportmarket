import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, writeFileSync, existsSync, unlinkSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const generator = fileURLToPath(new URL('../deploy/cloud-run-config.mjs', import.meta.url));
function check(fn) {
  const directory = mkdtempSync(join(tmpdir(), 'sportmarket-cloud-config-'));
  const env = { ...process.env, SPORTMARKET_USE_ENV_FILE: 'false', SPORTMARKET_SECRET_DIRECTORY: directory,
    SPORTMARKET_NEON_POOLED: 'postgresql://demo:dummy-password@db-pooler.example.test/app?sslmode=require',
    SPORTMARKET_NEON_DIRECT: 'postgresql://demo:dummy-password@db.example.test/app?sslmode=require' };
  try { fn(directory, env); }
  finally {
    for (const name of ['application.json', 'current.json']) {
      if (existsSync(join(directory, name))) unlinkSync(join(directory, name));
    }
    rmdirSync(directory);
  }
}
test('writes Spring configuration without printing credentials', () => check((directory, env) => {
  const result = spawnSync(process.execPath, [generator], { env, encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout, '');
  const config = JSON.parse(readFileSync(join(directory, 'application.json')));
  assert.match(config['spring.datasource.url'], /db-pooler/);
  assert.doesNotMatch(config['spring.flyway.url'], /db-pooler/);
  assert.doesNotMatch(config['spring.datasource.url'], /dummy-password/);
  assert.equal(config['spring.datasource.password'], 'dummy-password');
  assert.equal(Buffer.from(config['app.jwt.secret'], 'base64').length, 32);
}));
test('preserves the deployed signing key on redeploy', () => check((directory, env) => {
  const key = Buffer.alloc(32, 7).toString('base64');
  writeFileSync(join(directory, 'current.json'), JSON.stringify({ 'app.jwt.secret': key }));
  const result = spawnSync(process.execPath, [generator], { env, encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(JSON.parse(readFileSync(join(directory, 'application.json')))['app.jwt.secret'], key);
}));
test('rejects a pooled migration endpoint without echoing its password', () => check((directory, env) => {
  env.SPORTMARKET_NEON_DIRECT = env.SPORTMARKET_NEON_POOLED;
  const result = spawnSync(process.execPath, [generator], { env, encoding: 'utf8' });
  assert.notEqual(result.status, 0);
  assert.doesNotMatch(result.stdout + result.stderr, /dummy-password/);
  assert.equal(existsSync(join(directory, 'application.json')), false);
}));
