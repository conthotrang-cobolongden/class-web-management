import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { CLASS_STATUS_LABELS, fmtMoney } from '../../format.js';

const PAGE_SIZE = 20;
const GRADES = [6, 7, 8, 9];
const EMPTY_FORM = {
  name: '',
  grade: '6',
  school_year: '',
  start_date: '',
  end_date: '',
  default_monthly_fee: '',
  note: '',
};

export default function ClassesPage() {
  const { user: me } = useAuth();
  const isAdmin = me.role === 'admin';

  const [items, setItems] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [gradeFilter, setGradeFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(false);

  const [showCreate, setShowCreate] = useState(false);
  const [createForm, setCreateForm] = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const reqIdRef = useRef(0);
  const noticeTimerRef = useRef();

  const load = useCallback(async () => {
    const reqId = ++reqIdRef.current;
    setLoading(true);
    setError('');
    try {
      const params = new URLSearchParams({ page: String(page), page_size: String(PAGE_SIZE) });
      if (gradeFilter) params.set('grade', gradeFilter);
      if (statusFilter) params.set('status', statusFilter);
      const data = await api(`/classes?${params}`);
      if (reqId !== reqIdRef.current) return; // response cu ve muon -> bo
      // Trang hien tai khong con ton tai (total giam do loc) -> keo ve trang cuoi
      const maxPage = Math.max(1, Math.ceil(data.total / PAGE_SIZE));
      if (data.items.length === 0 && page > maxPage) {
        setPage(maxPage);
        return;
      }
      setItems(data.items);
      setTotal(data.total);
    } catch (err) {
      if (reqId === reqIdRef.current) setError(err.message);
    } finally {
      if (reqId === reqIdRef.current) setLoading(false);
    }
  }, [page, gradeFilter, statusFilter]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => () => clearTimeout(noticeTimerRef.current), []);

  function flash(msg) {
    clearTimeout(noticeTimerRef.current);
    setNotice(msg);
    noticeTimerRef.current = setTimeout(() => setNotice(''), 3000);
  }

  async function handleCreate(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    const body = {
      name: createForm.name.trim(),
      grade: Number(createForm.grade),
      school_year: createForm.school_year.trim(),
      start_date: createForm.start_date,
      end_date: createForm.end_date,
      default_monthly_fee: Number(createForm.default_monthly_fee),
    };
    if (createForm.note.trim()) body.note = createForm.note.trim();
    try {
      await api('/classes', { method: 'POST', body });
      setShowCreate(false);
      setCreateForm(EMPTY_FORM);
      flash('Đã tạo lớp mới');
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div>
      <div className="page-head">
        <h2>Lớp học ({total})</h2>
        {isAdmin && (
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setShowCreate((v) => !v)}
          >
            {showCreate ? 'Đóng form' : '+ Tạo lớp'}
          </button>
        )}
      </div>

      {error && <div className="alert">{error}</div>}
      {notice && <div className="alert alert-ok">{notice}</div>}

      {isAdmin && showCreate && (
        <form className="card stack" onSubmit={handleCreate}>
          <h3>Tạo lớp mới</h3>
          <div className="grid-2">
            <label>
              Tên lớp (VD: 6A)
              <input
                value={createForm.name}
                onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
                required
              />
            </label>
            <label>
              Khối
              <select
                value={createForm.grade}
                onChange={(e) => setCreateForm({ ...createForm, grade: e.target.value })}
              >
                {GRADES.map((g) => (
                  <option key={g} value={g}>Khối {g}</option>
                ))}
              </select>
            </label>
            <label>
              Năm học (VD: 2026-2027)
              <input
                value={createForm.school_year}
                onChange={(e) => setCreateForm({ ...createForm, school_year: e.target.value })}
                pattern="\d{4}-\d{4}"
                title="Định dạng: 2026-2027"
                placeholder="2026-2027"
                required
              />
            </label>
            <label>
              Phí tháng mặc định (VND)
              <input
                type="number"
                min={0}
                step={1000}
                value={createForm.default_monthly_fee}
                onChange={(e) => setCreateForm({ ...createForm, default_monthly_fee: e.target.value })}
                required
              />
            </label>
            <label>
              Ngày bắt đầu
              <input
                type="date"
                value={createForm.start_date}
                onChange={(e) => setCreateForm({ ...createForm, start_date: e.target.value })}
                required
              />
            </label>
            <label>
              Ngày kết thúc
              <input
                type="date"
                value={createForm.end_date}
                onChange={(e) => setCreateForm({ ...createForm, end_date: e.target.value })}
                required
              />
            </label>
            <label>
              Ghi chú
              <input
                value={createForm.note}
                onChange={(e) => setCreateForm({ ...createForm, note: e.target.value })}
              />
            </label>
          </div>
          <div>
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Đang tạo…' : 'Tạo lớp'}
            </button>
          </div>
        </form>
      )}

      <div className="filters">
        <select
          value={gradeFilter}
          onChange={(e) => {
            setGradeFilter(e.target.value);
            setPage(1);
          }}
        >
          <option value="">Tất cả khối</option>
          {GRADES.map((g) => (
            <option key={g} value={g}>Khối {g}</option>
          ))}
        </select>
        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value);
            setPage(1);
          }}
        >
          <option value="">Tất cả trạng thái</option>
          <option value="active">{CLASS_STATUS_LABELS.active}</option>
          <option value="closed">{CLASS_STATUS_LABELS.closed}</option>
        </select>
      </div>

      <div className="table-wrap table-cards">
        <table>
          <thead>
            <tr>
              <th>Tên lớp</th>
              <th>Khối</th>
              <th>Năm học</th>
              <th className="num">Phí tháng</th>
              <th className="num">Sĩ số</th>
              <th>GV chính</th>
              <th>Trạng thái</th>
            </tr>
          </thead>
          <tbody>
            {loading && [0, 1, 2].map((i) => (
              <tr key={`sk-${i}`}>
                <td colSpan={7}><div className="skeleton">Đang tải danh sách lớp…</div></td>
              </tr>
            ))}
            {!loading && items.length === 0 && (
              <tr><td colSpan={7} className="muted">Không có lớp nào</td></tr>
            )}
            {!loading && items.map((c) => (
              <tr key={c.id} className={c.status === 'closed' ? 'row-inactive' : ''}>
                <td className="td-title">
                  <Link to={`/classes/${c.id}`}>{c.name}</Link>
                </td>
                <td data-label="Khối">{c.grade}</td>
                <td data-label="Năm học">{c.school_year}</td>
                <td data-label="Phí tháng" className="num">{fmtMoney(c.default_monthly_fee)}</td>
                <td data-label="Sĩ số" className="num">{c.student_count}</td>
                <td data-label="GV chính">{c.main_teacher ? c.main_teacher.full_name : '—'}</td>
                <td data-label="Trạng thái">
                  <span className={`badge ${c.status === 'active' ? 'badge-ok' : 'badge-off'}`}>
                    {CLASS_STATUS_LABELS[c.status] || c.status}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="pager">
          <button
            type="button"
            className="btn btn-sm"
            disabled={page <= 1}
            onClick={() => setPage(page - 1)}
          >
            ← Trước
          </button>
          <span>Trang {page}/{totalPages}</span>
          <button
            type="button"
            className="btn btn-sm"
            disabled={page >= totalPages}
            onClick={() => setPage(page + 1)}
          >
            Sau →
          </button>
        </div>
      )}
    </div>
  );
}
