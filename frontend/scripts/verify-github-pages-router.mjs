import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const main = await readFile(new URL('../src/main.jsx', import.meta.url), 'utf8');

assert.ok(main.includes('<BrowserRouter basename={import.meta.env.BASE_URL}>'),
  'BrowserRouter must strip the Vite project-site base before matching existing routes');

console.log('GitHub Pages router base verified.');
