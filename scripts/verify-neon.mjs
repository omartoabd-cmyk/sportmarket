// Real HTTP/JDBC integration check. Mutations require an explicitly isolated Neon branch.
// Usage: node scripts/verify-neon.mjs .env.neon-verification
// Production read-only smoke: node scripts/verify-neon.mjs .env.local --read-only
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { readdirSync, openSync, closeSync, readFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { once } from 'node:events';
import { neonEnvironment } from './neon-env.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const backend = fileURLToPath(new URL('../sportmarket/', import.meta.url));
const envFile = process.argv[2];
const readOnly = process.argv.includes('--read-only');
if (!envFile) throw new Error('Pass an explicit ignored Neon environment file.');
const env = neonEnvironment(root, { ...process.env, NEON_ENV_FILE: envFile });
if (!readOnly && env.NEON_BRANCH !== 'sportmarket-neon-verification') {
  throw new Error('Write verification is restricted to the isolated sportmarket-neon-verification branch.');
}
const jar = readdirSync(backend + 'target').find(name => name.endsWith('.jar'));
if (!jar) throw new Error('Build the backend jar with mvn package first.');
const base = 'http://127.0.0.1:8082';
let server, logFd;
const logPath = backend + 'target/neon-verification-server.log';
async function start() {
  // Refuse to stop or interfere with any pre-existing application on this port.
  const occupied = await fetch(base + '/api/categories').then(() => true).catch(() => false);
  if (occupied) throw new Error('Port 8082 is already occupied; stop the verification server you started before retrying.');
  logFd = openSync(logPath, 'a');
  server = spawn('java', ['-jar', 'target/' + jar], {
    cwd: backend,
    env: { ...env, SPRING_PROFILES_ACTIVE: 'neon', SERVER_PORT: '8082', SERVER_ADDRESS: '127.0.0.1',
      IMAGE_STORAGE_DIR: backend + 'target/neon-verification-images' },
    stdio: ['ignore', logFd, logFd],
  });
  server.on('error', () => {});
  for (let attempt = 0; attempt < 120; attempt++) {
    if (server.exitCode !== null) throw new Error('Backend startup failed. Inspect the private target/neon-verification-server.log.');
    const ready = await fetch(base + '/api/categories').then(response => response.ok).catch(() => false);
    if (ready) return;
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  throw new Error('Backend did not become ready within 60 seconds. Inspect its private target log.');
}
async function stop() {
  if (server && server.exitCode === null) {
    const exited = once(server, 'exit');
    server.kill();
    await exited;
  }
  server = undefined;
  if (logFd !== undefined) closeSync(logFd);
  logFd = undefined;
}
async function api(path, { method = 'GET', body, token, expected = 200 } = {}) {
  const response = await fetch(base + '/api' + path, {
    method,
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  assert.equal(response.status, expected, `${method} ${path}: unexpected HTTP status`);
  return response.status === 204 ? undefined : response.json();
}
try {
  await start();
  const categories = await api('/categories');
  assert.equal(categories.length, 7);
  await api('/listings?size=2');
  const logs = readFileSync(logPath, 'utf8');
  assert.match(logs, /jdbc:postgresql:/);
  assert.match(logs, /neon\.tech/);
  assert.match(logs, /Successfully (applied|validated) 4 migrations/);
  console.log('PASS: Flyway V1-V4 and Hibernate validation on real Neon PostgreSQL; seven categories and paginated listings returned.');
  if (!readOnly) {
    const password = randomBytes(24).toString('base64url');
    async function account(label) {
      const email = 'neon-check-' + randomBytes(8).toString('hex') + '@example.test';
      await api('/auth/register', { method: 'POST', body: { fullName: label, email, password }, expected: 201 });
      return (await api('/auth/login', { method: 'POST', body: { email, password } })).accessToken;
    }
    const owner = await account('Neon Verification Seller');
    const other = await account('Neon Verification Other');
    const input = { title: 'Neon verification soccer ball', description: 'Temporary automated integration check.',
      price: 24.99, categoryId: categories[0].id, condition: 'GOOD' };
    await api('/listings', { method: 'POST', body: input, expected: 401 });
    await api('/listings', { method: 'POST', token: owner, body: { ...input, price: 0 }, expected: 400 });
    const listing = await api('/listings', { method: 'POST', body: input, token: owner, expected: 201 });
    await api('/listings/' + listing.id, { method: 'PUT', body: input, token: other, expected: 403 });
    const updated = await api('/listings/' + listing.id, { method: 'PUT', body: { ...input, price: 19.99 }, token: owner });
    assert.equal(updated.price, 19.99);
    assert.ok((await api('/users/me/listings', { token: owner })).some(value => value.id === listing.id));
    assert.equal((await api('/users/me/listings', { token: other })).length, 0);
    console.log('PASS: Registration, JWT login, listing create/update, validation, ownership rejection, and My Listings isolation.');
    await stop();
    await start();
    assert.equal((await api('/listings/' + listing.id)).price, 19.99);
    console.log('PASS: Listing survives a complete backend restart and is read back from Neon.');
    assert.equal((await api('/listings/' + listing.id + '/status', { method: 'PATCH', token: owner, body: { status: 'SOLD' } })).status, 'SOLD');
    const available = await api('/listings?search=Neon%20verification');
    assert.equal(available.totalElements, 0);
    await api('/listings/' + listing.id, { method: 'DELETE', token: other, expected: 403 });
    await api('/listings/' + listing.id, { method: 'DELETE', token: owner, expected: 204 });
    await api('/listings/' + listing.id, { expected: 404 });
    console.log('PASS: Sold filtering, non-owner delete rejection, owner deletion, and 404 after deletion.');
  }
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally { await stop(); }
