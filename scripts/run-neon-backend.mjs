import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { neonEnvironment } from './neon-env.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
let environment;
try { environment = neonEnvironment(root); }
catch (error) { console.error(error.message); process.exit(1); }
const child = spawn('mvn', ['spring-boot:run'], {
  cwd: fileURLToPath(new URL('../sportmarket/', import.meta.url)),
  env: environment,
  stdio: 'inherit',
  shell: process.platform === 'win32',
});
child.on('error', () => { console.error('Could not start Maven. Check that Java 21 and Maven are installed.'); process.exitCode = 1; });
child.on('exit', (code) => { process.exitCode = code ?? 1; });
