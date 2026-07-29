import { describe, expect, it } from 'vitest';
import { buildAuditQuery, localDateBounds } from './auditQuery.js';

describe('audit query', () => {
  it('omits empty filters and sends selected filters', () => {
    expect(buildAuditQuery({
      page: 2,
      category: 'fees',
      actorEmail: ' admin@test.local ',
      action: '',
      keyword: ' payment ',
      from: '',
      to: '',
    })).toBe('page=2&page_size=20&category=fees&actor_email=admin%40test.local&q=payment');
  });

  it('converts local dates to inclusive ISO bounds', () => {
    const bounds = localDateBounds('2026-07-29', '2026-07-29');
    expect(new Date(bounds.from).getTime()).toBe(
      new Date(2026, 6, 29, 0, 0, 0, 0).getTime());
    expect(new Date(bounds.to).getTime()).toBe(
      new Date(2026, 6, 29, 23, 59, 59, 999).getTime());
  });
});
