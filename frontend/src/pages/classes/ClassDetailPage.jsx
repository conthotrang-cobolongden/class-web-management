import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { ROLE_LABELS, useAuth } from '../../auth.jsx';
import {
  CLASS_STATUS_LABELS,
  DOW_LABELS,
  SESSION_STATUS_LABELS,
  STUDENT_STATUS_LABELS,
  fmtDate,
  fmtMoney,
  fmtTime,
} from '../../format.js';
import './classes.css';

const GRADES = [6, 7, 8, 9];
const GENDER_LABELS = { male: 'Nam', female: 'Nữ', other: 'Khác' };
const SESSIONS_PAGE_SIZE = 20;

// Cat "18:00:00" -> "18:00" de nhet vao <input type="time">
function toTimeInput(t) {
  return t ? String(t).slice(0, 5) : '';
}

function toDateInput(d) {
  return d ? String(d).slice(0, 10) : '';
}

/* ===================== Tab 1: Thong tin & Nhan su ===================== */

function InfoStaffTab({ cls, classId, isAdmin, reloadClass, flash }) {
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [editForm, setEditForm] = useState(null);

  const [users, setUsers] = useState([]);
  const [usersError, setUsersError] = useState('');
  const [staffForm, setStaffForm] = useState(null); // {main_teacher_id: '', assistant_ids: []}

  // Danh sach user active cho form phan cong — CHI goi khi la admin
  useEffect(() => {
    if (!isAdmin) return;
    let alive = true;
    api('/users?active=true&page_size=100')
      .then((data) => {
        if (alive) setUsers(data.items);
      })
      .catch((err) => {
        if (alive) setUsersError(err.message);
      });
    return () => {
      alive = false;
    };
  }, [isAdmin]);

  const mainStaff = cls.staff.find((s) => s.staff_role === 'main') || null;
  const assistants = cls.staff.filter((s) => s.staff_role === 'assistant');

  function openEdit() {
    setStaffForm(null);
    setEditForm({
      name: cls.name,
      grade: String(cls.grade),
      school_year: cls.school_year,
      start_date: toDateInput(cls.start_date),
      end_date: toDateInput(cls.end_date),
      default_monthly_fee: String(cls.default_monthly_fee ?? ''),
      status: cls.status,
      note: cls.note || '',
    });
  }

  function openStaff() {
    setEditForm(null);
    setStaffForm({
      main_teacher_id: mainStaff ? String(mainStaff.user_id) : '',
      assistant_ids: assistants.map((s) => s.user_id),
    });
  }

  async function handleEditSave(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await api(`/classes/${classId}`, {
        method: 'PUT',
        body: {
          name: editForm.name.trim(),
          grade: Number(editForm.grade),
          school_year: editForm.school_year.trim(),
          start_date: editForm.start_date,
          end_date: editForm.end_date,
          default_monthly_fee: Number(editForm.default_monthly_fee),
          status: editForm.status,
          note: editForm.note.trim(),
        },
      });
      setEditForm(null);
      flash('Đã cập nhật thông tin lớp');
      reloadClass();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleStaffSave(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    const mainId = Number(staffForm.main_teacher_id);
    try {
      await api(`/classes/${classId}/staff`, {
        method: 'PUT',
        body: {
          main_teacher_id: mainId,
          assistant_ids: staffForm.assistant_ids.filter((id) => id !== mainId),
        },
      });
      setStaffForm(null);
      flash('Đã cập nhật phân công nhân sự');
      reloadClass();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  function toggleAssistant(userId) {
    setStaffForm((f) => ({
      ...f,
      assistant_ids: f.assistant_ids.includes(userId)
        ? f.assistant_ids.filter((id) => id !== userId)
        : [...f.assistant_ids, userId],
    }));
  }

  return (
    <div>
      {error && <div className="alert">{error}</div>}

      <div className="card stack">
        <div className="subhead">
          <h3>Thông tin lớp</h3>
          {isAdmin && !editForm && (
            <button type="button" className="btn btn-sm" onClick={openEdit}>
              Sửa thông tin
            </button>
          )}
        </div>
        {!editForm && (
          <div className="grid-2">
            <div><span className="muted">Khối:</span> {cls.grade}</div>
            <div><span className="muted">Năm học:</span> {cls.school_year}</div>
            <div><span className="muted">Thời gian:</span> {fmtDate(cls.start_date)} – {fmtDate(cls.end_date)}</div>
            <div><span className="muted">Phí tháng mặc định:</span> {fmtMoney(cls.default_monthly_fee)}</div>
            <div>
              <span className="muted">Trạng thái:</span>{' '}
              <span className={`badge ${cls.status === 'active' ? 'badge-ok' : 'badge-off'}`}>
                {CLASS_STATUS_LABELS[cls.status] || cls.status}
              </span>
            </div>
            <div><span className="muted">Ghi chú:</span> {cls.note || '—'}</div>
          </div>
        )}
        {editForm && (
          <form className="stack" onSubmit={handleEditSave}>
            <div className="grid-2">
              <label>
                Tên lớp
                <input
                  value={editForm.name}
                  onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                  required
                />
              </label>
              <label>
                Khối
                <select
                  value={editForm.grade}
                  onChange={(e) => setEditForm({ ...editForm, grade: e.target.value })}
                >
                  {GRADES.map((g) => (
                    <option key={g} value={g}>Khối {g}</option>
                  ))}
                </select>
              </label>
              <label>
                Năm học
                <input
                  value={editForm.school_year}
                  onChange={(e) => setEditForm({ ...editForm, school_year: e.target.value })}
                  pattern="\d{4}-\d{4}"
                  title="Định dạng: 2026-2027"
                  required
                />
              </label>
              <label>
                Phí tháng mặc định (VND)
                <input
                  type="number"
                  min={0}
                  step={1000}
                  value={editForm.default_monthly_fee}
                  onChange={(e) => setEditForm({ ...editForm, default_monthly_fee: e.target.value })}
                  required
                />
              </label>
              <label>
                Ngày bắt đầu
                <input
                  type="date"
                  value={editForm.start_date}
                  onChange={(e) => setEditForm({ ...editForm, start_date: e.target.value })}
                  required
                />
              </label>
              <label>
                Ngày kết thúc
                <input
                  type="date"
                  value={editForm.end_date}
                  onChange={(e) => setEditForm({ ...editForm, end_date: e.target.value })}
                  required
                />
              </label>
              <label>
                Trạng thái
                <select
                  value={editForm.status}
                  onChange={(e) => setEditForm({ ...editForm, status: e.target.value })}
                >
                  <option value="active">{CLASS_STATUS_LABELS.active}</option>
                  <option value="closed">{CLASS_STATUS_LABELS.closed}</option>
                </select>
              </label>
              <label>
                Ghi chú
                <input
                  value={editForm.note}
                  onChange={(e) => setEditForm({ ...editForm, note: e.target.value })}
                />
              </label>
            </div>
            <div className="row-gap">
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Đang lưu…' : 'Lưu'}
              </button>
              <button type="button" className="btn btn-ghost" disabled={submitting} onClick={() => setEditForm(null)}>
                Hủy
              </button>
            </div>
          </form>
        )}
      </div>

      <div className="card stack">
        <div className="subhead">
          <h3>Nhân sự</h3>
          {isAdmin && !staffForm && (
            <button type="button" className="btn btn-sm" onClick={openStaff}>
              Phân công nhân sự
            </button>
          )}
        </div>
        {!staffForm && (
          <div className="stack">
            <div>
              <span className="muted">GV chính:</span>{' '}
              {mainStaff ? mainStaff.full_name : <span className="muted">Chưa gán</span>}
            </div>
            <div>
              <span className="muted">Trợ giảng:</span>{' '}
              {assistants.length === 0
                ? <span className="muted">Chưa có</span>
                : assistants.map((s) => s.full_name).join(', ')}
            </div>
          </div>
        )}
        {staffForm && (
          <form className="stack" onSubmit={handleStaffSave}>
            {usersError && <div className="alert">{usersError}</div>}
            <label>
              GV chính
              <select
                value={staffForm.main_teacher_id}
                onChange={(e) => setStaffForm({ ...staffForm, main_teacher_id: e.target.value })}
                required
              >
                <option value="">— Chọn GV chính —</option>
                {users.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.full_name} ({ROLE_LABELS[u.role] || u.role})
                  </option>
                ))}
              </select>
            </label>
            <div>
              <div className="field-label">Trợ giảng</div>
              {users.length === 0 && <div className="muted">Đang tải danh sách tài khoản…</div>}
              <div className="grid-2">
                {users.map((u) => {
                  const isMain = String(u.id) === staffForm.main_teacher_id;
                  return (
                    <label key={u.id} className="check">
                      <input
                        type="checkbox"
                        checked={!isMain && staffForm.assistant_ids.includes(u.id)}
                        disabled={isMain}
                        onChange={() => toggleAssistant(u.id)}
                      />
                      {u.full_name} ({ROLE_LABELS[u.role] || u.role})
                      {isMain && <span className="muted"> — đã là GV chính</span>}
                    </label>
                  );
                })}
              </div>
            </div>
            <div className="row-gap">
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Đang lưu…' : 'Lưu phân công'}
              </button>
              <button type="button" className="btn btn-ghost" disabled={submitting} onClick={() => setStaffForm(null)}>
                Hủy
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}

/* ===================== Tab 2: Lich & Buoi hoc ===================== */

function ScheduleSessionsTab({ cls, classId, isManager, reloadClass, flash }) {
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // --- Khung tuan ---
  const [scheduleRows, setScheduleRows] = useState(null); // null = khong sua

  // --- Danh sach buoi ---
  const [sessions, setSessions] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [loadingSessions, setLoadingSessions] = useState(false);
  const reqIdRef = useRef(0);

  const [cancelTarget, setCancelTarget] = useState(null); // session dang huy
  const [cancelReason, setCancelReason] = useState('');

  const [showMakeup, setShowMakeup] = useState(false);
  const [makeupForm, setMakeupForm] = useState({
    session_date: '',
    start_time: '',
    end_time: '',
    makeup_for_session_id: '',
    note: '',
  });

  const loadSessions = useCallback(async () => {
    const reqId = ++reqIdRef.current;
    setLoadingSessions(true);
    setError('');
    try {
      const params = new URLSearchParams({
        class_id: String(classId),
        page: String(page),
        page_size: String(SESSIONS_PAGE_SIZE),
      });
      if (from) params.set('from', from);
      if (to) params.set('to', to);
      const data = await api(`/sessions?${params}`);
      if (reqId !== reqIdRef.current) return; // response cu ve muon -> bo
      const maxPage = Math.max(1, Math.ceil(data.total / SESSIONS_PAGE_SIZE));
      if (data.items.length === 0 && page > maxPage) {
        setPage(maxPage);
        return;
      }
      setSessions(data.items);
      setTotal(data.total);
    } catch (err) {
      if (reqId === reqIdRef.current) setError(err.message);
    } finally {
      if (reqId === reqIdRef.current) setLoadingSessions(false);
    }
  }, [classId, page, from, to]);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  function openScheduleEdit() {
    setScheduleRows(
      cls.schedules.map((s) => ({
        day_of_week: String(s.day_of_week),
        start_time: toTimeInput(s.start_time),
        end_time: toTimeInput(s.end_time),
      })),
    );
  }

  async function handleScheduleSave(e) {
    e.preventDefault();
    setError('');
    for (const row of scheduleRows) {
      if (row.end_time <= row.start_time) {
        setError(`Giờ kết thúc phải sau giờ bắt đầu (${DOW_LABELS[row.day_of_week]} ${row.start_time}–${row.end_time})`);
        return;
      }
    }
    setSubmitting(true);
    try {
      await api(`/classes/${classId}/schedules`, {
        method: 'PUT',
        body: scheduleRows.map((r) => ({
          day_of_week: Number(r.day_of_week),
          start_time: r.start_time,
          end_time: r.end_time,
        })),
      });
      setScheduleRows(null);
      flash('Đã cập nhật khung tuần');
      reloadClass();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleGenerate() {
    if (!window.confirm('Sinh buổi học từ khung tuần trong khoảng thời gian của lớp? Các buổi đã tồn tại sẽ được bỏ qua.')) return;
    setError('');
    setSubmitting(true);
    try {
      const data = await api(`/classes/${classId}/generate-sessions`, { method: 'POST' });
      flash(`Đã sinh ${data.created} buổi mới, bỏ qua ${data.skipped} buổi đã có`);
      loadSessions();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCancelSubmit(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const data = await api(`/sessions/${cancelTarget.id}/cancel`, {
        method: 'POST',
        body: { cancel_reason: cancelReason.trim() },
      });
      setCancelTarget(null);
      setCancelReason('');
      flash(data.message || 'Đã hủy buổi học');
      loadSessions();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleMakeupSubmit(e) {
    e.preventDefault();
    setError('');
    if (makeupForm.end_time <= makeupForm.start_time) {
      setError('Giờ kết thúc phải sau giờ bắt đầu');
      return;
    }
    setSubmitting(true);
    const body = {
      class_id: Number(classId),
      session_date: makeupForm.session_date,
      start_time: makeupForm.start_time,
      end_time: makeupForm.end_time,
      is_makeup: true,
    };
    if (makeupForm.makeup_for_session_id) body.makeup_for_session_id = Number(makeupForm.makeup_for_session_id);
    if (makeupForm.note.trim()) body.note = makeupForm.note.trim();
    try {
      await api('/sessions', { method: 'POST', body });
      setShowMakeup(false);
      setMakeupForm({ session_date: '', start_time: '', end_time: '', makeup_for_session_id: '', note: '' });
      flash('Đã tạo buổi bù');
      loadSessions();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  const cancelledSessions = sessions.filter((s) => s.status === 'cancelled');
  const totalPages = Math.max(1, Math.ceil(total / SESSIONS_PAGE_SIZE));

  return (
    <div>
      {error && <div className="alert">{error}</div>}

      <div className="card stack">
        <div className="subhead">
          <h3>Khung giờ cố định theo tuần</h3>
          {isManager && scheduleRows === null && (
            <button type="button" className="btn btn-sm" onClick={openScheduleEdit}>
              Sửa khung tuần
            </button>
          )}
        </div>

        {scheduleRows === null && (
          cls.schedules.length === 0
            ? <div className="muted">Chưa có khung tuần</div>
            : (
              <div className="wrap-row">
                {cls.schedules.map((s) => (
                  <span key={s.id} className="badge badge-ok">
                    {DOW_LABELS[s.day_of_week]} {fmtTime(s.start_time)}–{fmtTime(s.end_time)}
                  </span>
                ))}
              </div>
            )
        )}

        {scheduleRows !== null && (
          <form className="stack" onSubmit={handleScheduleSave}>
            {scheduleRows.length === 0 && (
              <div className="muted">Chưa có khung nào — lưu danh sách rỗng sẽ xóa toàn bộ khung tuần</div>
            )}
            {scheduleRows.map((row, idx) => (
              <div key={idx} className="wrap-row">
                <select
                  value={row.day_of_week}
                  onChange={(e) => {
                    const rows = [...scheduleRows];
                    rows[idx] = { ...row, day_of_week: e.target.value };
                    setScheduleRows(rows);
                  }}
                >
                  {Object.entries(DOW_LABELS).map(([v, label]) => (
                    <option key={v} value={v}>{label}</option>
                  ))}
                </select>
                <input
                  type="time"
                  value={row.start_time}
                  onChange={(e) => {
                    const rows = [...scheduleRows];
                    rows[idx] = { ...row, start_time: e.target.value };
                    setScheduleRows(rows);
                  }}
                  required
                />
                <span>–</span>
                <input
                  type="time"
                  value={row.end_time}
                  onChange={(e) => {
                    const rows = [...scheduleRows];
                    rows[idx] = { ...row, end_time: e.target.value };
                    setScheduleRows(rows);
                  }}
                  required
                />
                <button
                  type="button"
                  className="btn btn-sm btn-ghost"
                  onClick={() => setScheduleRows(scheduleRows.filter((_, i) => i !== idx))}
                >
                  Xóa
                </button>
              </div>
            ))}
            <div className="row-gap">
              <button
                type="button"
                className="btn btn-sm"
                onClick={() => setScheduleRows([...scheduleRows, { day_of_week: '1', start_time: '', end_time: '' }])}
              >
                + Thêm khung
              </button>
            </div>
            <div className="row-gap">
              <button type="submit" className="btn btn-primary" disabled={submitting}>
                {submitting ? 'Đang lưu…' : 'Lưu khung tuần'}
              </button>
              <button type="button" className="btn btn-ghost" disabled={submitting} onClick={() => setScheduleRows(null)}>
                Hủy
              </button>
            </div>
          </form>
        )}

        {isManager && (
          <div>
            <button type="button" className="btn" disabled={submitting} onClick={handleGenerate}>
              Sinh buổi học từ khung tuần
            </button>
          </div>
        )}
      </div>

      {cancelTarget && (
        <form className="card stack" onSubmit={handleCancelSubmit}>
          <h3>Hủy buổi ngày {fmtDate(cancelTarget.session_date)} ({fmtTime(cancelTarget.start_time)}–{fmtTime(cancelTarget.end_time)})</h3>
          <label>
            Lý do hủy (bắt buộc)
            <input
              value={cancelReason}
              onChange={(e) => setCancelReason(e.target.value)}
              required
              autoFocus
            />
          </label>
          <div className="row-gap">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Đang hủy…' : 'Xác nhận hủy buổi'}
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              disabled={submitting}
              onClick={() => {
                setCancelTarget(null);
                setCancelReason('');
              }}
            >
              Không hủy nữa
            </button>
          </div>
        </form>
      )}

      {isManager && showMakeup && (
        <form className="card stack" onSubmit={handleMakeupSubmit}>
          <h3>Tạo buổi bù</h3>
          <div className="grid-2">
            <label>
              Ngày học bù
              <input
                type="date"
                value={makeupForm.session_date}
                onChange={(e) => setMakeupForm({ ...makeupForm, session_date: e.target.value })}
                required
              />
            </label>
            <label>
              Bù cho buổi đã hủy (tùy chọn)
              <select
                value={makeupForm.makeup_for_session_id}
                onChange={(e) => setMakeupForm({ ...makeupForm, makeup_for_session_id: e.target.value })}
              >
                <option value="">— Không liên kết —</option>
                {cancelledSessions.map((s) => (
                  <option key={s.id} value={s.id}>
                    {fmtDate(s.session_date)} {fmtTime(s.start_time)}–{fmtTime(s.end_time)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Giờ bắt đầu
              <input
                type="time"
                value={makeupForm.start_time}
                onChange={(e) => setMakeupForm({ ...makeupForm, start_time: e.target.value })}
                required
              />
            </label>
            <label>
              Giờ kết thúc
              <input
                type="time"
                value={makeupForm.end_time}
                onChange={(e) => setMakeupForm({ ...makeupForm, end_time: e.target.value })}
                required
              />
            </label>
            <label>
              Ghi chú
              <input
                value={makeupForm.note}
                onChange={(e) => setMakeupForm({ ...makeupForm, note: e.target.value })}
              />
            </label>
          </div>
          <div className="row-gap">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Đang tạo…' : 'Tạo buổi bù'}
            </button>
            <button type="button" className="btn btn-ghost" disabled={submitting} onClick={() => setShowMakeup(false)}>
              Đóng
            </button>
          </div>
        </form>
      )}

      <div className="page-head subhead">
        <h3>Danh sách buổi học</h3>
        {isManager && !showMakeup && (
          <button type="button" className="btn btn-primary btn-sm" onClick={() => setShowMakeup(true)}>
            + Tạo buổi bù
          </button>
        )}
      </div>

      <div className="filters">
        <label className="check">
          Từ ngày
          <input
            type="date"
            value={from}
            onChange={(e) => {
              setFrom(e.target.value);
              setPage(1);
            }}
          />
        </label>
        <label className="check">
          Đến ngày
          <input
            type="date"
            value={to}
            onChange={(e) => {
              setTo(e.target.value);
              setPage(1);
            }}
          />
        </label>
      </div>

      <div className="table-wrap table-cards">
        <table>
          <thead>
            <tr>
              <th>Ngày</th>
              <th>Giờ</th>
              <th>Trạng thái</th>
              <th className="num">Điểm danh</th>
              <th>Ghi chú / Lý do hủy</th>
              {isManager && <th />}
            </tr>
          </thead>
          <tbody>
            {loadingSessions && [0, 1, 2].map((i) => (
              <tr key={`sk-${i}`}>
                <td colSpan={isManager ? 6 : 5}><div className="skeleton">Đang tải danh sách buổi học…</div></td>
              </tr>
            ))}
            {!loadingSessions && sessions.length === 0 && (
              <tr><td colSpan={isManager ? 6 : 5} className="muted">Chưa có buổi học nào</td></tr>
            )}
            {!loadingSessions && sessions.map((s) => (
              <tr key={s.id} className={s.status === 'cancelled' ? 'row-inactive' : ''}>
                <td className="td-title">{fmtDate(s.session_date)}</td>
                <td data-label="Giờ">{fmtTime(s.start_time)}–{fmtTime(s.end_time)}</td>
                <td data-label="Trạng thái">
                  <span className={`badge ${s.status === 'cancelled' ? 'badge-danger' : 'badge-ok'}`}>
                    {SESSION_STATUS_LABELS[s.status] || s.status}
                  </span>
                  {s.is_makeup && <span className="badge badge-warn badge-gap">Buổi bù</span>}
                </td>
                <td data-label="Điểm danh" className="num">{s.attendance_count}</td>
                <td data-label={s.status === 'cancelled' ? 'Lý do hủy' : 'Ghi chú'}>
                  {s.status === 'cancelled' ? (s.cancel_reason || '—') : (s.note || '—')}
                </td>
                {isManager && (
                  <td className="td-actions">
                    {s.status === 'scheduled' && (
                      <button
                        type="button"
                        className="btn btn-sm btn-ghost"
                        onClick={() => {
                          setCancelTarget(s);
                          setCancelReason('');
                          window.scrollTo({ top: 0, behavior: 'smooth' });
                        }}
                      >
                        Hủy buổi
                      </button>
                    )}
                  </td>
                )}
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

/* ===================== Tab 3: Hoc sinh ===================== */

function StudentsTab({ classId }) {
  const [items, setItems] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const reqIdRef = useRef(0);

  const load = useCallback(async () => {
    const reqId = ++reqIdRef.current;
    setLoading(true);
    setError('');
    try {
      const data = await api(`/classes/${classId}/students`);
      if (reqId !== reqIdRef.current) return;
      setItems(data.items);
    } catch (err) {
      if (reqId === reqIdRef.current) setError(err.message);
    } finally {
      if (reqId === reqIdRef.current) setLoading(false);
    }
  }, [classId]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div>
      {error && <div className="alert">{error}</div>}
      <div className="table-wrap table-cards">
        <table>
          <thead>
            <tr>
              <th>Họ tên</th>
              <th>Ngày sinh</th>
              <th>Giới tính</th>
              <th>SĐT phụ huynh</th>
              <th>Trạng thái</th>
              <th>Vào lớp</th>
              <th className="num">Phí tháng riêng</th>
            </tr>
          </thead>
          <tbody>
            {loading && [0, 1, 2].map((i) => (
              <tr key={`sk-${i}`}>
                <td colSpan={7}><div className="skeleton">Đang tải danh sách học sinh…</div></td>
              </tr>
            ))}
            {!loading && items.length === 0 && (
              <tr><td colSpan={7} className="muted">Lớp chưa có học sinh nào</td></tr>
            )}
            {!loading && items.map((st) => (
              <tr key={st.student_id}>
                <td className="td-title">
                  <Link to={`/students/${st.student_id}`}>{st.full_name}</Link>
                </td>
                <td data-label="Ngày sinh">{fmtDate(st.dob)}</td>
                <td data-label="Giới tính">{GENDER_LABELS[st.gender] || '—'}</td>
                <td data-label="SĐT phụ huynh">{st.parent_phone || '—'}</td>
                <td data-label="Trạng thái">
                  <span className={`badge ${st.status === 'active' ? 'badge-ok' : 'badge-off'}`}>
                    {STUDENT_STATUS_LABELS[st.status] || st.status}
                  </span>
                </td>
                <td data-label="Vào lớp">{fmtDate(st.joined_at)}</td>
                <td data-label="Phí tháng riêng" className="num">{st.custom_monthly_fee != null ? fmtMoney(st.custom_monthly_fee) : <span className="muted">Theo lớp</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/* ===================== Trang chi tiet lop ===================== */

const TABS = [
  { key: 'info', label: 'Thông tin & Nhân sự' },
  { key: 'sessions', label: 'Lịch & Buổi học' },
  { key: 'students', label: 'Học sinh' },
];

export default function ClassDetailPage() {
  const { id } = useParams();
  const { user: me } = useAuth();
  const isAdmin = me.role === 'admin';

  const [cls, setCls] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState('info');
  const reqIdRef = useRef(0);
  const noticeTimerRef = useRef();

  const loadClass = useCallback(async () => {
    const reqId = ++reqIdRef.current;
    setLoading(true);
    setError('');
    try {
      const data = await api(`/classes/${id}`);
      if (reqId !== reqIdRef.current) return; // dieu huong sang lop khac giua chung -> bo
      setCls(data.class);
    } catch (err) {
      if (reqId === reqIdRef.current) setError(err.message);
    } finally {
      if (reqId === reqIdRef.current) setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    setCls(null);
    setTab('info');
    loadClass();
  }, [loadClass]);

  useEffect(() => () => clearTimeout(noticeTimerRef.current), []);

  function flash(msg) {
    clearTimeout(noticeTimerRef.current);
    setNotice(msg);
    noticeTimerRef.current = setTimeout(() => setNotice(''), 3000);
  }

  // manager = admin HOAC GV chinh cua lop (backend van la chot chan that)
  const isManager = isAdmin
    || (cls != null && cls.staff.some((s) => s.staff_role === 'main' && s.user_id === me.id));

  if (loading && !cls) {
    return (
      <div className="stack">
        <div className="skeleton skeleton-line" />
        <div className="skeleton skeleton-block" />
        <div className="skeleton skeleton-block" />
      </div>
    );
  }

  if (!cls) {
    return (
      <div>
        {error && <div className="alert">{error}</div>}
        <Link to="/classes">← Quay lại danh sách lớp</Link>
      </div>
    );
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <div className="back-link">
            <Link to="/classes">← Danh sách lớp</Link>
          </div>
          <h2 className="page-title">
            Lớp {cls.name}{' '}
            <span className={`badge ${cls.status === 'active' ? 'badge-ok' : 'badge-off'}`}>
              {CLASS_STATUS_LABELS[cls.status] || cls.status}
            </span>
          </h2>
        </div>
      </div>

      {error && <div className="alert">{error}</div>}
      {notice && <div className="alert alert-ok">{notice}</div>}

      <div className="tab-bar">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            className={`btn ${tab === t.key ? 'btn-primary' : ''}`}
            onClick={() => setTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'info' && (
        <InfoStaffTab
          cls={cls}
          classId={id}
          isAdmin={isAdmin}
          reloadClass={loadClass}
          flash={flash}
        />
      )}
      {tab === 'sessions' && (
        <ScheduleSessionsTab
          cls={cls}
          classId={id}
          isManager={isManager}
          reloadClass={loadClass}
          flash={flash}
        />
      )}
      {tab === 'students' && <StudentsTab classId={id} />}
    </div>
  );
}
