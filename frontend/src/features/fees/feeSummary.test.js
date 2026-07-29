import { describe, expect, it } from 'vitest';
import { summaryForRole } from './feeSummary.js';

const rows = [
  { amount_due: 1_000_000, paid_total: 1_000_000, status: 'paid' },
  { amount_due: 1_000_000, paid_total: 0, status: 'unpaid' },
  { amount_due: 1_000_000, paid_total: 0, status: 'partial' },
];

describe('summaryForRole', () => {
  it('returns only the unpaid student count for assistants', () => {
    expect(summaryForRole('assistant', rows)).toEqual({ owingCount: 2 });
  });

  it('keeps detailed aggregates for teachers', () => {
    expect(summaryForRole('teacher', rows)).toMatchObject({
      due: 3_000_000,
      paid: 1_000_000,
      left: 2_000_000,
      owingCount: 2,
      paidPct: 33,
    });
  });
});
