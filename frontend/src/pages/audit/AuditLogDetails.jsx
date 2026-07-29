function snapshotText(value) {
  if (value == null) return 'Không có dữ liệu';
  if (typeof value === 'string') return value;
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

export function Snapshot({ title, value }) {
  return (
    <section className="audit-snapshot">
      <h4>{title}</h4>
      <pre>{snapshotText(value)}</pre>
    </section>
  );
}

function formatTimestamp(value) {
  if (!value) return 'Không xác định';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
  }).format(date);
}

export default function AuditLogDetails({ audit }) {
  const actor = audit.actor;
  return (
    <div className="audit-details">
      <dl className="audit-metadata">
        <div><dt>Thời gian</dt><dd>{formatTimestamp(audit.created_at)}</dd></div>
        <div><dt>Người thao tác</dt><dd>{actor?.full_name || 'Không xác định'}</dd></div>
        <div><dt>Email</dt><dd>{actor?.email || 'Không xác định'}</dd></div>
        <div><dt>Danh mục</dt><dd>{audit.category || '—'}</dd></div>
        <div><dt>Hành động</dt><dd>{audit.action || '—'}</dd></div>
        <div><dt>Bảng / bản ghi</dt><dd>{audit.table_name || '—'} / {audit.record_id ?? '—'}</dd></div>
        <div className="audit-metadata-wide"><dt>Mô tả</dt><dd>{audit.description || '—'}</dd></div>
      </dl>
      <div className="audit-snapshots">
        <Snapshot title="Dữ liệu trước" value={audit.old_values} />
        <Snapshot title="Dữ liệu sau" value={audit.new_values} />
      </div>
    </div>
  );
}