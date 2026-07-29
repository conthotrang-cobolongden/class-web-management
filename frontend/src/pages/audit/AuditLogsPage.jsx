import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../api.js';
import {
  AUDIT_PAGE_SIZE,
  buildAuditQuery,
} from '../../features/audit/auditQuery.js';
import AuditLogDetails from './AuditLogDetails.jsx';
import './audit-logs.css';

const CATEGORIES = [
  'authentication', 'students', 'classes', 'sessions', 'attendance',
  'grades', 'fees', 'users', 'imports',
];

const ACTIONS = [
  'create', 'update', 'delete', 'login_success', 'login_failure', 'import',
];

function formatTimestamp(value) {
  if (!value) return 'Không xác định';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date);
}

function actorLabel(item) {
  return item.actor?.email || item.actor?.full_name || 'Không xác định';
}

function LoadingState() {
  return (
    <div className="audit-loading" role="status" aria-label="Đang tải nhật ký">
      <span className="sr-only">Đang tải nhật ký…</span>
      {[0, 1, 2].map((value) => (
        <div className="audit-skeleton-row" data-testid="audit-skeleton" key={value} aria-hidden="true">
          <span className="skeleton" />
          <span className="skeleton" />
          <span className="skeleton" />
        </div>
      ))}
    </div>
  );
}

export default function AuditLogsPage() {
  const [items, setItems] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [category, setCategory] = useState('');
  const [actorEmail, setActorEmail] = useState('');
  const [debouncedEmail, setDebouncedEmail] = useState('');
  const [keyword, setKeyword] = useState('');
  const [debouncedKeyword, setDebouncedKeyword] = useState('');
  const [action, setAction] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [selectedRecord, setSelectedRecord] = useState(null);
  const [expandedRecordId, setExpandedRecordId] = useState(null);
  const requestId = useRef(0);
  const drawerCloseRef = useRef(null);
  const drawerTriggerRef = useRef(null);
  const drawerRef = useRef(null);

  useEffect(() => {
    const timer = setTimeout(() => {
      setPage(1);
      setDebouncedEmail(actorEmail);
    }, 350);
    return () => clearTimeout(timer);
  }, [actorEmail]);

  useEffect(() => {
    const timer = setTimeout(() => {
      setPage(1);
      setDebouncedKeyword(keyword);
    }, 350);
    return () => clearTimeout(timer);
  }, [keyword]);

  const load = useCallback(async () => {
    const current = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const data = await api(`/audit-logs?${buildAuditQuery({
        page,
        category,
        actorEmail: debouncedEmail,
        action,
        from,
        to,
        keyword: debouncedKeyword,
      })}`);
      if (current !== requestId.current) return;
      setItems(data.items);
      setTotal(data.total);
    } catch (caught) {
      if (current !== requestId.current) return;
      setError(caught);
    } finally {
      if (current === requestId.current) setLoading(false);
    }
  }, [page, category, debouncedEmail, action, from, to, debouncedKeyword]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (!selectedRecord) return undefined;
    drawerCloseRef.current?.focus();
    const handleDrawerKeyDown = (event) => {
      if (event.key === 'Escape') {
        closeDrawer();
        return;
      }
      if (event.key !== 'Tab') return;
      const focusable = drawerRef.current?.querySelectorAll(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      );
      if (!focusable?.length) return;
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const active = document.activeElement;
      if (event.shiftKey && (active === first || !drawerRef.current.contains(active))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && active === last) {
        event.preventDefault();
        first.focus();
      }
    };
    const closeAtMobileBreakpoint = () => {
      if (window.innerWidth <= 768) setSelectedRecord(null);
    };
    document.addEventListener('keydown', handleDrawerKeyDown);
    window.addEventListener('resize', closeAtMobileBreakpoint);
    return () => {
      document.removeEventListener('keydown', handleDrawerKeyDown);
      window.removeEventListener('resize', closeAtMobileBreakpoint);
    };
  }, [selectedRecord]);

  const totalPages = Math.max(1, Math.ceil(total / AUDIT_PAGE_SIZE));
  const filtersActive = Boolean(category || actorEmail || keyword || action || from || to);

  function changeImmediate(setter) {
    return (event) => {
      setter(event.target.value);
      setPage(1);
      closeDetails();
    };
  }

  function changeText(setter) {
    return (event) => {
      setter(event.target.value);
      closeDetails();
    };
  }

  function closeDetails() {
    setSelectedRecord(null);
    setExpandedRecordId(null);
  }

  function openDrawer(item, event) {
    drawerTriggerRef.current = event.currentTarget;
    setSelectedRecord(item);
  }

  function closeDrawer() {
    setSelectedRecord(null);
    requestAnimationFrame(() => drawerTriggerRef.current?.focus());
  }

  function resetFilters() {
    setCategory('');
    setActorEmail('');
    setDebouncedEmail('');
    setKeyword('');
    setDebouncedKeyword('');
    setAction('');
    setFrom('');
    setTo('');
    setPage(1);
    closeDetails();
  }

  function movePage(nextPage) {
    closeDetails();
    setPage(nextPage);
  }

  return (
    <main className="audit-page">
      <div className="audit-page-content" inert={selectedRecord ? '' : undefined}>
      <header className="page-head audit-page-head">
        <div>
          <h2>Nhật ký hoạt động</h2>
          <p className="muted">Lịch sử thay đổi chỉ đọc của hệ thống</p>
        </div>
      </header>

      <div className="filters audit-filters" aria-label="Bộ lọc nhật ký">
        <label>
          Danh mục
          <select value={category} onChange={changeImmediate(setCategory)}>
            <option value="">Tất cả</option>
            {CATEGORIES.map((value) => <option key={value} value={value}>{value}</option>)}
          </select>
        </label>
        <label>
          Email người thao tác
          <input type="search" value={actorEmail} onChange={changeText(setActorEmail)} />
        </label>
        <label>
          Từ khóa
          <input type="search" value={keyword} onChange={changeText(setKeyword)} />
        </label>
        <label>
          Hành động
          <select value={action} onChange={changeImmediate(setAction)}>
            <option value="">Tất cả</option>
            {ACTIONS.map((value) => <option key={value} value={value}>{value}</option>)}
          </select>
        </label>
        <label>
          Từ ngày
          <input type="date" value={from} onChange={changeImmediate(setFrom)} />
        </label>
        <label>
          Đến ngày
          <input type="date" value={to} onChange={changeImmediate(setTo)} />
        </label>
      </div>

      {loading && <LoadingState />}

      {!loading && error?.status === 403 && (
        <div className="audit-state audit-state-forbidden" role="alert">
          <h3>Bạn không có quyền xem nhật ký</h3>
          <p>Vui lòng liên hệ quản trị viên nếu bạn cần quyền truy cập.</p>
        </div>
      )}

      {!loading && error && error.status !== 403 && (
        <div className="audit-state audit-state-error" role="alert">
          <h3>Không thể tải nhật ký</h3>
          <p>{error.message || 'Đã xảy ra lỗi. Vui lòng thử lại.'}</p>
          <button className="btn" type="button" onClick={load}>Thử lại</button>
        </div>
      )}

      {!loading && !error && items.length === 0 && (
        <div className="audit-state audit-empty-state">
          <h3>Không có nhật ký phù hợp</h3>
          <p>Thử thay đổi hoặc xóa các bộ lọc hiện tại.</p>
          {filtersActive && <button className="btn" type="button" onClick={resetFilters}>Xóa bộ lọc</button>}
        </div>
      )}

      {!loading && !error && items.length > 0 && (
        <>
          <div className="table-wrap audit-desktop-table" aria-label="Kết quả nhật ký">
            <table>
              <thead>
                <tr>
                  <th>Thời gian</th>
                  <th>Người thao tác</th>
                  <th>Danh mục</th>
                  <th>Hành động</th>
                  <th>Mô tả</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id}>
                    <td>{formatTimestamp(item.created_at)}</td>
                    <td>{actorLabel(item)}</td>
                    <td><span className="badge badge-off">{item.category || '—'}</span></td>
                    <td>{item.action || '—'}</td>
                    <td className="audit-description-cell">
                      <button type="button" className="audit-row-button" onClick={(event) => openDrawer(item, event)}>
                        {item.description || `Bản ghi #${item.id}`}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="audit-mobile-list" aria-label="Thẻ nhật ký">
            {items.map((item) => {
              const expanded = expandedRecordId === item.id;
              const panelId = `audit-card-details-${item.id}`;
              return (
                <article className="audit-card" key={item.id}>
                  <button
                    type="button"
                    className="audit-card-toggle"
                    aria-expanded={expanded}
                    aria-controls={panelId}
                    onClick={() => setExpandedRecordId(expanded ? null : item.id)}
                  >
                    <span className="audit-card-title">{item.description || `Bản ghi #${item.id}`}</span>
                    <span className="audit-card-summary">
                      <span>{formatTimestamp(item.created_at)}</span>
                      <span>{actorLabel(item)}</span>
                    </span>
                    <span className="audit-card-chevron" aria-hidden="true">{expanded ? '−' : '+'}</span>
                  </button>
                  {expanded && (
                    <div className="audit-card-details" id={panelId}>
                      <AuditLogDetails audit={item} />
                    </div>
                  )}
                </article>
              );
            })}
          </div>
        </>
      )}

      {!loading && !error && totalPages > 1 && (
        <nav className="pager" aria-label="Phân trang nhật ký">
          <button className="btn btn-sm" type="button" disabled={page <= 1} onClick={() => movePage(page - 1)}>← Trước</button>
          <span aria-live="polite">Trang {page}/{totalPages}</span>
          <button className="btn btn-sm" type="button" disabled={page >= totalPages} onClick={() => movePage(page + 1)}>Sau →</button>
        </nav>
      )}

      </div>
      {selectedRecord && (
        <>
          <button className="audit-drawer-backdrop" type="button" aria-label="Đóng chi tiết nhật ký" onClick={closeDrawer} />
          <aside ref={drawerRef} className="audit-drawer" role="dialog" aria-modal="true" aria-labelledby="audit-drawer-title">
            <header className="audit-drawer-header">
              <div>
                <p className="muted">Bản ghi #{selectedRecord.id}</p>
                <h3 id="audit-drawer-title">Chi tiết nhật ký</h3>
              </div>
              <button ref={drawerCloseRef} className="btn btn-sm" type="button" onClick={closeDrawer}>Đóng</button>
            </header>
            <AuditLogDetails audit={selectedRecord} />
          </aside>
        </>
      )}
    </main>
  );
}