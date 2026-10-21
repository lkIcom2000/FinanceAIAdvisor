// Regenerates the API client from the committed OpenAPI snapshot into a temporary directory and fails
// if it differs from the committed client in src/app/api (AD-19: no hand-written or stale DTOs).
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readdirSync, readFileSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, relative } from 'node:path';

const committedDir = 'src/app/api';
const tempDir = mkdtempSync(join(tmpdir(), 'fine-api-'));

function listFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? listFiles(path) : [path];
  });
}

function snapshot(dir) {
  const files = new Map();
  for (const path of listFiles(dir)) {
    files.set(relative(dir, path).replaceAll('\\', '/'), readFileSync(path, 'utf8').replaceAll('\r\n', '\n'));
  }
  return files;
}

try {
  execFileSync(
    process.execPath,
    ['node_modules/ng-openapi-gen/lib/index.js', '--config', 'ng-openapi-gen.json', '--output', tempDir],
    { stdio: ['ignore', 'ignore', 'inherit'] },
  );

  let committed;
  try {
    committed = snapshot(committedDir);
  } catch {
    committed = new Map();
  }
  const generated = snapshot(tempDir);

  const differences = [
    ...[...generated.keys()].filter((f) => !committed.has(f)).map((f) => `missing in ${committedDir}: ${f}`),
    ...[...committed.keys()].filter((f) => !generated.has(f)).map((f) => `not generated (stale): ${f}`),
    ...[...generated.keys()]
      .filter((f) => committed.has(f) && committed.get(f) !== generated.get(f))
      .map((f) => `differs: ${f}`),
  ];

  if (differences.length > 0) {
    console.error('The committed API client does not match openapi/openapi.json:');
    for (const line of differences) console.error(`  - ${line}`);
    console.error("Run 'npm run api:generate' and commit the result.");
    process.exit(1);
  }
  console.log(`API client is up to date (${generated.size} files).`);
} finally {
  rmSync(tempDir, { recursive: true, force: true });
}
