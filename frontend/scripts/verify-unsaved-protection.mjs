import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const [attendance, grades] = await Promise.all([
  readFile(new URL('../src/pages/attendance/AttendancePage.jsx', import.meta.url), 'utf8'),
  readFile(new URL('../src/pages/grades/GradesPage.jsx', import.meta.url), 'utf8'),
]);

for (const [name, source] of [['attendance', attendance], ['grades', grades]]) {
  assert.ok(source.includes("addEventListener('beforeunload'"), `${name} must warn before browser exit with unsaved edits`);
  assert.ok(source.includes("removeEventListener('beforeunload'"), `${name} must clean up its browser exit guard`);
}

assert.ok(attendance.includes('confirmDiscardChanges'),
  'attendance must protect class and session changes when the roster is dirty');

console.log('Unsaved-change protection verified.');
