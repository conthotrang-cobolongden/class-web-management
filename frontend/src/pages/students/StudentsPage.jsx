import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { STUDENT_STATUS_LABELS, fmtDate } from '../../format.js';
import StudentImportPanel from './StudentImportPanel.jsx';

const PAGE_SIZE = 20;

// Nhan gioi tinh (schema: male/female/other) — dung chung voi trang chi tiet
export const GENDER_LABELS = { male: 'Nam', female: 'Nữ', other: 'Khác' };

// Mau badge theo trang thai HS — dung chung voi trang chi tiet
export const STUDENT_STATUS_BADGE = { active: 'badge-ok', paused: 'badge-warn', left: 'badge-off' };

// Ngay hom nay theo gio LOCAL (khong dung toISOString vi lech mui gio UTC)
export function todayStr() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

const emptyForm = () => ({
  full_name: '',
  dob: '',
  gender: '',
  school_name: '',
  parent_name: '',
  parent_phone: '',
  enrollment_date: todayStr(),
  class_id: '',
  custom_monthly_fee: '',
  note: '',
});

export default function StudentsPage() {
  const { user: me } = useAuth();
  const [items, setItems] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [q, setQ] = useState('');
  const [qDebounced, setQDebounced] = useState('');
  const [classFilter, setClassFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [classes, setClasses] = useState([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(false);

  const [showCreate, setShowCreate] = useState(false);
  const [showImport, setShowImport] = useState(false);
  const [createForm, setCreateForm] = useState(emptyForm);
  const [submitting, setSubmitting] = useState(false);
  const reqIdRef = useRef(0);
  const noticeTimerRef = useRef();

  const canCreate = me.role === 'admin' || me.role === 'teacher';

  // Debounce 300ms cho o tim kiem; doi tu khoa thi keo ve trang 1
  useEffect(() => {
    const t = setTimeout(() => {
      setQDebounced(q);
      setPage(1);
    }, 300);
    return () => clearTimeout(t);
  }, [q]);

  // Lay danh sach lop lam option loc + option form tao (admin: tat ca, GV/TG: lop minh)
  useEffect(() => {
    let cancelled = false;
    api('/classes?page_size=100')
      .then((data) => {
        if (!cancelled) setClasses(data.items);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const load = useCallback(async () => {
    const reqId = ++reqIdRef.current;
    setLoading(true);
    setError('');
    try {
      const params = new URLSearchParams({ page: String(page), page_size: String(PAGE_SIZE) });
      if (qDebounced.trim()) params.set('q', qDebounced.trim());
      if (classFilter) params.set('class_id', classFilter);
      if (statusFilter) params.set('status', statusFilter);
      const data = await api(`/students?${params}`);
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
  }, [page, qDebounced, classFilter, statusFilter]);

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
      full_name: createForm.full_name.trim(),
      enrollment_date: createForm.enrollment_date,
    };
    if (createForm.dob) body.dob = createForm.dob;
    if (createForm.gender) body.gender = createForm.gender;
    if (createForm.school_name.trim()) body.school_name = createForm.school_name.trim();
    if (createForm.parent_name.trim()) body.parent_name = createForm.parent_name.trim();
    if (createForm.parent_phone.trim()) body.parent_phone = createForm.parent_phone.trim();
    if (createForm.note.trim()) body.note = createForm.note.trim();
    if (createForm.class_id) {
      body.class_id = Number(createForm.class_id);
      if (createForm.custom_monthly_fee !== '') {
        body.custom_monthly_fee = Number(createForm.custom_monthly_fee);
      }
    }
    try {
      await api('/students', { method: 'POST', body });
      setShowCreate(false);
      setCreateForm(emptyForm());
      flash('Đã thêm học sinh');
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  // Lop duoc phep chon khi tao HS: lop active; GV chinh chi duoc lop minh lam GV chinh
  const creatableClasses = classes.filter(
    (c) => c.status === 'active' && (me.role === 'admin' || (c.main_teacher && c.main_teacher.id === me.id)),
  );

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div>
      <div className="page-head">
        <h2>Học sinh ({total})</h2>
        <div className="row-gap">
          <button
            type="button"
            className="btn"
            onClick={() => {
              setShowImport((value) => !value);
              setShowCreate(false);
            }}
          >
            {showImport ? 'Đóng nhập Excel' : 'Nhập từ Excel'}
          </button>
          {canCreate && (
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => {
                setShowCreate((value) => !value);
                setShowImport(false);
              }}
            >
              {showCreate ? 'Đóng form' : '+ Thêm học sinh'}
            </button>
          )}
        </div>
      </div>

      {error && <div className="alert">{error}</div>}
      {showImport && (
        <StudentImportPanel
          classes={classes}
          onImported={(data) => {
            flash(`Đã nhập ${data.imported} học sinh`);
            load();
          }}
          onNotice={flash}
        />
      )}

      {notice && <div className="alert alert-ok">{notice}</div>}

      {showCreate && canCreate && (
        <form className="card stack" onSubmit={handleCreate}>
          <h3>Thêm học sinh mới</h3>
          <div className="grid-2">
            <label>
              Họ tên
              <input
                value={createForm.full_name}
                onChange={(e) => setCreateForm({ ...createForm, full_name: e.target.value })}
                required
              />
            </label>
            <label>
              Ngày sinh
              <input
                type="date"
                value={createForm.dob}
                onChange={(e) => setCreateForm({ ...createForm, dob: e.target.value })}
              />
            </label>
            <label>
              Giới tính
              <select
                value={createForm.gender}
                onChange={(e) => setCreateForm({ ...createForm, gender: e.target.value })}
              >
                <option value="">— Chưa rõ —</option>
                {Object.entries(GENDER_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </label>
            <label>
              Trường đang học (chính khóa)
              <input
                value={createForm.school_name}
                onChange={(e) => setCreateForm({ ...createForm, school_name: e.target.value })}
              />
            </label>
            <label>
              Tên phụ huynh
              <input
                value={createForm.parent_name}
                onChange={(e) => setCreateForm({ ...createForm, parent_name: e.target.value })}
              />
            </label>
            <label>
              SĐT phụ huynh
              <input
                value={createForm.parent_phone}
                onChange={(e) => setCreateForm({ ...createForm, parent_phone: e.target.value })}
              />
            </label>
            <label>
              Ngày vào học
              <input
                type="date"
                value={createForm.enrollment_date}
                onChange={(e) => setCreateForm({ ...createForm, enrollment_date: e.target.value })}
                required
              />
            </label>
            <label>
              Lớp
              <select
                value={createForm.class_id}
                onChange={(e) => setCreateForm({ ...createForm, class_id: e.target.value })}
                required={me.role === 'teacher'}
              >
                <option value="">
                  {me.role === 'teacher' ? '— Chọn lớp (bắt buộc) —' : '— Chưa xếp lớp —'}
                </option>
                {creatableClasses.map((c) => (
                  <option key={c.id} value={c.id}>{c.name} ({c.school_year})</option>
                ))}
              </select>
            </label>
            <label>
              Phí ưu đãi (₫/tháng, bỏ trống = theo phí lớp)
              <input
                type="number"
                min={0}
                value={createForm.custom_monthly_fee}
                disabled={!createForm.class_id}
                title={!createForm.class_id ? 'Chọn lớp trước khi đặt phí ưu đãi' : undefined}
                onChange={(e) => setCreateForm({ ...createForm, custom_monthly_fee: e.target.value })}
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
          {me.role === 'teacher' && (
            <p className="muted" style={{ margin: 0 }}>
              Giáo viên chính phải chọn một lớp mình phụ trách khi thêm học sinh.
            </p>
          )}
          <div>
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Đang lưu…' : 'Thêm học sinh'}
            </button>
          </div>
        </form>
      )}

      <div className="filters">
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Tìm theo tên hoặc mã học sinh…"
        />
        <select
          value={classFilter}
          onChange={(e) => {
            setClassFilter(e.target.value);
            setPage(1);
          }}
        >
          <option value="">Tất cả lớp</option>
          {classes.map((c) => (
            <option key={c.id} value={c.id}>{c.name} ({c.school_year})</option>
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
          {Object.entries(STUDENT_STATUS_LABELS).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
      </div>

      <div className="table-wrap table-cards">
        <table>
          <thead>
            <tr>
              <th>Họ tên</th>
              <th>Mã HS</th>
              <th>Ngày sinh</th>
              <th>Lớp hiện tại</th>
              <th>SĐT phụ huynh</th>
              <th>Trạng thái</th>
            </tr>
          </thead>
          <tbody>
            {loading && [1, 2, 3].map((i) => (
              <tr key={`sk-${i}`}>
                <td colSpan={6}>
                  <div className="skeleton" style={{ height: 18, width: `${95 - i * 18}%` }} />
                </td>
              </tr>
            ))}
            {!loading && items.length === 0 && (
              <tr><td colSpan={6} className="muted">Không có học sinh nào</td></tr>
            )}
            {!loading && items.map((s) => (
              <tr key={s.id} className={s.status === 'left' ? 'row-inactive' : ''}>
                <td className="td-title">
                  <Link to={`/students/${s.id}`}>{s.full_name}</Link>
                </td>
                <td data-label="Mã HS">
                  <span className="badge badge-info">{s.student_code}</span>
                </td>
                <td data-label="Ngày sinh">{fmtDate(s.dob)}</td>
                <td data-label="Lớp hiện tại">{s.current_class ? s.current_class.name : '—'}</td>
                <td className="num" data-label="SĐT phụ huynh">{s.parent_phone || '—'}</td>
                <td data-label="Trạng thái">
                  <span className={`badge ${STUDENT_STATUS_BADGE[s.status] || 'badge-off'}`}>
                    {STUDENT_STATUS_LABELS[s.status] || s.status}
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
