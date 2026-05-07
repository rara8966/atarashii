import { cpSync, existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const root = process.cwd();
const wasmSource = join(root, 'node_modules', 'esbuild-wasm');
const targets = [
  join(root, 'node_modules', 'esbuild'),
  join(root, 'node_modules', 'vite', 'node_modules', 'esbuild')
];

if (!existsSync(wasmSource)) {
  process.exit(0);
}

for (const target of targets) {
  if (!existsSync(target)) continue;
  cpSync(wasmSource, target, { recursive: true, force: true });
  const packagePath = join(target, 'package.json');
  if (existsSync(packagePath)) {
    const packageJson = JSON.parse(readFileSync(packagePath, 'utf8'));
    packageJson.name = 'esbuild';
    writeFileSync(packagePath, `${JSON.stringify(packageJson, null, 2)}\n`);
  }
}