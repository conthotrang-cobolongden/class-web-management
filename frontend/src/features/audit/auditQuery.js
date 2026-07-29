export const AUDIT_PAGE_SIZE = 20;

export function localDateBounds(from, to) {
  return {
    from: from ? new Date(`${from}T00:00:00`).toISOString() : '',
    to: to ? new Date(`${to}T23:59:59.999`).toISOString() : '',
  };
}

export function buildAuditQuery(filters) {
  const params = new URLSearchParams({
    page: String(filters.page || 1),
    page_size: String(AUDIT_PAGE_SIZE),
  });
  const dates = localDateBounds(filters.from, filters.to);
  const entries = {
    category: filters.category,
    actor_email: filters.actorEmail,
    action: filters.action,
    from: dates.from,
    to: dates.to,
    q: filters.keyword,
  };
  Object.entries(entries).forEach(([key, raw]) => {
    const value = typeof raw === 'string' ? raw.trim() : raw;
    if (value) params.set(key, value);
  });
  return params.toString();
}
