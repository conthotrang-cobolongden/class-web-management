import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const [baseCss, designCss, workflowCss, gradesJsx] = await Promise.all([
  readFile(new URL('../src/styles.css', import.meta.url), 'utf8'),
  readFile(new URL('../src/design-system.css', import.meta.url), 'utf8'),
  readFile(new URL('../src/workflow-overrides.css', import.meta.url), 'utf8'),
  readFile(new URL('../src/pages/grades/GradesPage.jsx', import.meta.url), 'utf8'),
]);
const globalCss = `${baseCss}\n${designCss}\n${workflowCss}`;

for (const token of [
  '--color-forest-700:',
  '--color-clay-500:',
  '--color-cream-50:',
  '--surface-elevated:',
  '--control-touch:',
  '--content-max:',
]) {
  assert.ok(globalCss.includes(token), `missing shared design token ${token}`);
}

assert.match(globalCss, /@media\s*\(min-width:\s*769px\)\s*and\s*\(max-width:\s*1100px\)/,
  'tablet-specific navigation behavior is required');
assert.match(globalCss, /@media\s*\(min-width:\s*1600px\)/,
  'large desktop content behavior is required');
assert.ok(globalCss.includes('.table-cards td[data-label]'),
  'mobile record cards must retain labeled fields');
assert.ok(globalCss.includes('.att-save-bar'), 'attendance must keep a prominent sticky save workflow');
assert.match(globalCss, /\.att-status\s*\{[^}]*min-height:\s*var\(--control-touch\)/s,
  'attendance status actions must use touch-sized controls');
assert.ok(gradesJsx.includes('inputMode="decimal"'),
  'grade entry must preserve mobile numeric input intent');
assert.ok(globalCss.includes('.grade-input.changed'),
  'grade entry must expose an unsaved visual state');

console.log('Design-system contract verified.');
