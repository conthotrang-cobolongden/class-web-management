import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const viteConfig = await readFile(new URL('../vite.config.js', import.meta.url), 'utf8');
const workflowUrl = new URL('../../.github/workflows/deploy-pages.yml', import.meta.url);
const workflow = await readFile(workflowUrl, 'utf8').catch(() => '');

assert.ok(viteConfig.includes("base: '/class-web-management/'"),
  'Vite must emit assets under the GitHub project-site base path');
assert.ok(workflow.includes('working-directory: frontend'),
  'Pages workflow must build from the frontend directory');
assert.ok(workflow.includes('path: frontend/dist'),
  'Pages workflow must publish frontend/dist so index.html is at artifact root');
assert.ok(workflow.includes('cp frontend/dist/index.html frontend/dist/404.html'),
  'Pages workflow must preserve BrowserRouter deep-link refreshes');

console.log('GitHub Pages deployment contract verified.');
