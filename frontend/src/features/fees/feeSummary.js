export function summaryForRole(role, rows = []) {
  const owingCount = rows.filter((fee) => fee.status !== 'paid').length;
  if (role === 'assistant') {
    return { owingCount };
  }

  const due = rows.reduce((sum, fee) => sum + Number(fee.amount_due || 0), 0);
  const paid = rows.reduce((sum, fee) => sum + Number(fee.paid_total || 0), 0);
  const left = rows.reduce(
    (sum, fee) => sum + Math.max(0, Number(fee.amount_due || 0) - Number(fee.paid_total || 0)),
    0,
  );
  return {
    due,
    paid,
    left,
    owingCount,
    paidPct: due > 0 ? Math.round((paid / due) * 100) : 0,
  };
}
