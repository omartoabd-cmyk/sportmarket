import test from 'node:test';
import assert from 'node:assert/strict';
import { jdbcConnection, neonEnvironment } from './neon-env.mjs';
import { mkdtempSync, writeFileSync, unlinkSync, rmdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

test('adapts credentials and libpq TLS parameters without leaking them into JDBC URL', () => {
  const result = jdbcConnection('postgresql://demo%40role:p%40ss%3Aword@db.example.test/app?sslmode=require&channel_binding=require');
  assert.equal(result.username, 'demo@role');
  assert.equal(result.password, 'p@ss:word');
  assert.equal(result.url, 'jdbc:postgresql://db.example.test/app?sslmode=require&channelBinding=require');
});
test('requires TLS and reports invalid input without echoing it', () => {
  assert.throws(() => jdbcConnection('postgresql://demo:dummy@db.example.test/app?sslmode=disable'), /require TLS/);
  assert.throws(() => jdbcConnection('sensitive-invalid-input'), error => !error.message.includes('sensitive-invalid-input'));
});
test('loads quoted env vars and separates pooled application and direct migration connections', () => {
  const root = mkdtempSync(join(tmpdir(), 'sportmarket-neon-'));
  try {
    writeFileSync(join(root, '.env.local'), 'DATABASE_URL="postgresql://demo:dummy@db-pooler.example.test/app"\nDATABASE_URL_UNPOOLED="postgresql://demo:dummy@db.example.test/app"\n');
    const env = neonEnvironment(root, {});
    assert.match(env.DB_URL, /db-pooler/);
    assert.doesNotMatch(env.DB_MIGRATION_URL, /db-pooler/);
    assert.equal(Buffer.from(env.JWT_SECRET, 'base64').length, 32);
    assert.equal(env.SPRING_PROFILES_ACTIVE, 'neon');
    assert.throws(() => neonEnvironment(root, { DATABASE_URL_UNPOOLED: 'postgresql://demo:dummy@db-pooler.example.test/app' }), /non-pooled/);
  } finally { unlinkSync(join(root, '.env.local')); rmdirSync(root); }
});
