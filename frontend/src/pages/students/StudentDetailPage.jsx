import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { STUDENT_STATUS_LABELS, fmtDate, fmtMoney } from '../../format.js';
import { GENDER_LABELS, STUDENT_STATUS_BADGE, todayStr } from './StudentsPage.jsx';

const LEAVE_REASON_LABELS = { transferred: 'Chuyển lớp', left: 'Nghỉ học' };

export default function StudentDetailPage() {
  const { id } = useParams();
  const { user: me } = useAuth();
  const [student, setStudent] = useState(null);
  const [classes, setClasses] = useState([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);

  // panel: null | 'edit' | 'transfer' | 'fee' | 'enroll' — mo toi da 1 form thao tac
  const [panel, setPanel] = useState(null);
  const [editForm, setEditForm] = useState(null);
  const [transferForm, setTransferForm] = useState({ to_class_id: '', effective_date: todayStr() });
  const [feeValue, setFeeValue] = useState('');
  const [enrollForm, setEnrollForm] = useState({ class_id: '', joined_at: todayStr(), custom_monthly_fee: '' });
  const [submitting, setSubmitting] = useState(false);
  const reqIdRef = useRef(0);
  const noticeTimerRef = useRef();

  const canManage = me.role === 'admin' || me.role === 'teacher';

  const load = useCallback(async () => {
    const reqId = ++reqIdRef.current;
    setLoading(true);
    setError('');
    try {
      const data = await api(`/students/${id}`);
      if (reqId !== reqIdRef.current) return; // doi trang giua chung -> bo response cu
      setStudent(data.student);
    } catch (err) {
      if (reqId === reqIdRef.current) {
        setStudent(null);
        setError(err.message);
      }
    } finally {
      if (reqId === reqIdRef.current) setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  // Doi sang HS khac -> dong moi form dang mo
  useEffect(() => {
    setPanel(null);
    setEditForm(null);
  }, [id]);

  // Danh sach lop: option chuyen lop / gan lop + tra ten lop dich trong lich su
  useEffect(() => {
    let cancelled = false;
    api('/classes?page_size=100')
      .then((data) => {
        if (!cancelled) setClasses(data.items);
      })
      .catch(() => {
        // khong chan xem ho so neu tai danh sach lop loi; form chuyen lop se trong
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => () => clearTimeout(noticeTimerRef.current), []);

  function flash(msg) {
    clearTimeout(noticeTimerRef.current);
    setNotice(msg);
    noticeTimerRef.current = setTimeout(() => setNotice(''), 3000);
  }

  const classMap = useMemo(() => {
    const m = {};
    classes.forEach((c) => {
      m[c.id] = c;
    });
    return m;
  }, [classes]);

  const enrollments = useMemo(() => {
    if (!student || !student.enrollments) return [];
    // Sap xep moi nhat len tren (enrollment mo dung dau)
    return [...student.enrollments].sort((a, b) => {
      if (!a.left_at && b.left_at) return -1;
      if (a.left_at && !b.left_at) return 1;
      return String(b.joined_at).localeCompare(String(a.joined_at));
    });
  }, [student]);

  const openEnrollment = useMemo(
    () => enrollments.find((e) => !e.left_at) || null,
    [enrollments],
  );

  function openPanel(name) {
    setError('');
    if (name === 'edit') {
      setEditForm({
        full_name: student.full_name || '',
        dob: student.dob ? String(student.dob).slice(0, 10) : '',
        gender: student.gender || '',
        school_name: student.school_name || '',
        parent_name: student.parent_name || '',
        parent_phone: student.parent_phone || '',
        enrollment_date: student.enrollment_date ? String(student.enrollment_date).slice(0, 10) : '',
        status: student.status || 'active',
        note: student.note || '',
      });
    }
    if (name === 'transfer') setTransferForm({ to_class_id: '', effective_date: todayStr() });
    if (name === 'fee') {
      setFeeValue(
        openEnrollment && openEnrollment.custom_monthly_fee != null
          ? String(openEnrollment.custom_monthly_fee)
          : '',
      );
    }
    if (name === 'enroll') setEnrollForm({ class_id: '', joined_at: todayStr(), custom_monthly_fee: '' });
    setPanel((cur) => (cur === name ? null : name));
  }

  async function handleEditSave(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    const body = {
      full_name: editForm.full_name.trim(),
      dob: editForm.dob || null,
      gender: editForm.gender || null,
      school_name: editForm.school_name.trim() || null,
      parent_name: editForm.parent_name.trim() || null,
      parent_phone: editForm.parent_phone.trim() || null,
      enrollment_date: editForm.enrollment_date,
      status: editForm.status,
      note: editForm.note.trim() || null,
    };
    try {
      await api(`/students/${id}`, { method: 'PUT', body });
      setPanel(null);
      flash('Đã cập nhật hồ sơ');
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleTransfer(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const data = await api(`/students/${id}/transfer`, {
        method: 'POST',
        body: {
          to_class_id: Number(transferForm.to_class_id),
          effective_date: transferForm.effective_date,
        },
      });
      setPanel(null);
      flash((data && data.message) || 'Đã chuyển lớp');
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleFeeSave(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await api(`/students/${id}/custom-fee`, {
        method: 'PUT',
        body: { custom_monthly_fee: feeValue === '' ? null : Number(feeValue) },
      });
      setPanel(null);
      flash('Đã cập nhật phí ưu đãi');
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleEnroll(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    const body = {
      class_id: Number(enrollForm.class_id),
      joined_at: enrollForm.joined_at,
    };
    if (enrollForm.custom_monthly_fee !== '') {
      body.custom_monthly_fee = Number(enrollForm.custom_monthly_fee);
    }
    try {
      await api(`/students/${id}/enroll`, { method: 'POST', body });
      setPanel(null);
      flash('Đã gán học sinh vào lớp');
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  // Lop dich chuyen lop: active + khac lop hien tai
  const transferTargets = classes.filter(
    (c) => c.status === 'active' && (!openEnrollment || c.id !== openEnrollment.class_id),
  );
  // Lop de gan HS chua co lop: active; GV chinh chi duoc lop minh lam GV chinh
  const enrollTargets = classes.filter(
    (c) => c.status === 'active' && (me.role === 'admin' || (c.main_teacher && c.main_teacher.id === me.id)),
  );

  if (!student) {
    return (
      <div>
        <p><Link to="/students">← Danh sách học sinh</Link></p>
        {error && <div className="alert">{error}</div>}
        {loading && (
          <div className="card stack">
            <div className="skeleton" style={{ height: 22, width: '45%' }} />
            <div className="skeleton" style={{ height: 16, width: '75%' }} />
            <div className="skeleton" style={{ height: 16, width: '60%' }} />
          </div>
        )}
        {!loading && !error && <p className="muted">Không có dữ liệu học sinh</p>}
      </div>
    );
  }

  return (
    <div>
      <p><Link to="/students">← Danh sách học sinh</Link></p>

      <div className="page-head">
        <h2>
          {student.full_name}{' '}
          <span className={`badge ${STUDENT_STATUS_BADGE[student.status] || 'badge-off'}`}>
            {STUDENT_STATUS_LABELS[student.status] || student.status}
          </span>
        </h2>
        {canManage && (
          <div className="row-gap">
            <button type="button" className="btn" onClick={() => openPanel('edit')}>
              Sửa hồ sơ
            </button>
            {openEnrollment && (
              <>
                <button type="button" className="btn" onClick={() => openPanel('transfer')}>
                  Chuyển lớp
                </button>
                <button type="button" className="btn" onClick={() => openPanel('fee')}>
                  Phí ưu đãi
                </button>
              </>
            )}
            {!openEnrollment && (
              <button type="button" className="btn" onClick={() => openPanel('enroll')}>
                Gán vào lớp
              </button>
            )}
          </div>
        )}
      </div>

      {error && <div className="alert">{error}</div>}
      {notice && <div className="alert alert-ok">{notice}</div>}

      <div className="card">
        <h3>Hồ sơ</h3>
        <div className="grid-2">
          <div>
            <div className="muted">Ngày sinh</div>
            <div>{fmtDate(student.dob)}</div>
          </div>
          <div>
            <div className="muted">Giới tính</div>
            <div>{GENDER_LABELS[student.gender] || '—'}</div>
          </div>
          <div>
            <div className="muted">Trường chính khóa</div>
            <div>{student.school_name || '—'}</div>
          </div>
          <div>
            <div className="muted">Ngày vào học</div>
            <div>{fmtDate(student.enrollment_date)}</div>
          </div>
          <div>
            <div className="muted">Phụ huynh</div>
            <div>{student.parent_name || '—'}</div>
          </div>
          <div>
            <div className="muted">SĐT phụ huynh</div>
            <div>{student.parent_phone || '—'}</div>
          </div>
          <div>
            <div className="muted">Lớp hiện tại</div>
            <div>
              {openEnrollment ? (
                <Link to={`/classes/${openEnrollment.class_id}`}>{openEnrollment.class_name}</Link>
              ) : (
                'Chưa xếp lớp'
              )}
            </div>
          </div>
          <div>
            <div className="muted">Phí ưu đãi hiện tại</div>
            <div>
              {openEnrollment
                ? (openEnrollment.custom_monthly_fee != null
                  ? fmtMoney(openEnrollment.custom_monthly_fee)
                  : 'Theo phí lớp')
                : '—'}
            </div>
          </div>
        </div>
        {student.note && (
          <p style={{ marginBottom: 0 }}>
            <span className="muted">Ghi chú:</span> {student.note}
          </p>
        )}
      </div>

      {panel === 'edit' && editForm && (
        <form className="card stack" onSubmit={handleEditSave}>
          <h3>Sửa hồ sơ</h3>
          <div className="grid-2">
            <label>
              Họ tên
              <input
                value={editForm.full_name}
                onChange={(e) => setEditForm({ ...editForm, full_name: e.target.value })}
                required
              />
            </label>
            <label>
              Ngày sinh
              <input
                type="date"
                value={editForm.dob}
                onChange={(e) => setEditForm({ ...editForm, dob: e.target.value })}
              />
            </label>
            <label>
              Giới tính
              <select
                value={editForm.gender}
                onChange={(e) => setEditForm({ ...editForm, gender: e.target.value })}
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
                value={editForm.school_name}
                onChange={(e) => setEditForm({ ...editForm, school_name: e.target.value })}
              />
            </label>
            <label>
              Tên phụ huynh
              <input
                value={editForm.parent_name}
                onChange={(e) => setEditForm({ ...editForm, parent_name: e.target.value })}
              />
            </label>
            <label>
              SĐT phụ huynh
              <input
                value={editForm.parent_phone}
                onChange={(e) => setEditForm({ ...editForm, parent_phone: e.target.value })}
              />
            </label>
            <label>
              Ngày vào học
              <input
                type="date"
                value={editForm.enrollment_date}
                onChange={(e) => setEditForm({ ...editForm, enrollment_date: e.target.value })}
                required
              />
            </label>
            <label>
              Trạng thái
              <select
                value={editForm.status}
                onChange={(e) => setEditForm({ ...editForm, status: e.target.value })}
              >
                {Object.entries(STUDENT_STATUS_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
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
            <button type="button" className="btn btn-ghost" disabled={submitting} onClick={() => setPanel(null)}>
              Hủy
            </button>
          </div>
        </form>
      )}

      {panel === 'transfer' && openEnrollment && (
        <form className="card stack narrow" onSubmit={handleTransfer}>
          <h3>Chuyển lớp</h3>
          <p className="muted" style={{ margin: 0 }}>
            Từ lớp <strong>{openEnrollment.class_name}</strong> — dữ liệu điểm danh, điểm số,
            học phí ở lớp cũ được giữ nguyên để tra cứu.
          </p>
          <label>
            Lớp đích
            <select
              value={transferForm.to_class_id}
              onChange={(e) => setTransferForm({ ...transferForm, to_class_id: e.target.value })}
              required
            >
              <option value="">— Chọn lớp —</option>
              {transferTargets.map((c) => (
                <option key={c.id} value={c.id}>{c.name} ({c.school_year})</option>
              ))}
            </select>
          </label>
          <label>
            Ngày hiệu lực
            <input
              type="date"
              value={transferForm.effective_date}
              onChange={(e) => setTransferForm({ ...transferForm, effective_date: e.target.value })}
              required
            />
          </label>
          <div className="row-gap">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Đang chuyển…' : 'Xác nhận chuyển lớp'}
            </button>
            <button type="button" className="btn btn-ghost" disabled={submitting} onClick={() => setPanel(null)}>
              Hủy
            </button>
          </div>
        </form>
      )}

      {panel === 'fee' && openEnrollment && (
        <form className="card stack narrow" onSubmit={handleFeeSave}>
          <h3>Phí ưu đãi — lớp {openEnrollment.class_name}</h3>
          <label>
            Phí tháng riêng cho học sinh này (₫)
            <input
              type="number"
              min={0}
              value={feeValue}
              onChange={(e) => setFeeValue(e.target.value)}
              placeholder="Bỏ trống = theo phí lớp"
            />
          </label>
          <p className="muted" style={{ margin: 0 }}>
            Bỏ trống rồi lưu để quay về đóng theo phí mặc định của lớp.
          </p>
          <div className="row-gap">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Đang lưu…' : 'Lưu phí ưu đãi'}
            </button>
            <button type="button" className="btn btn-ghost" disabled={submitting} onClick={() => setPanel(null)}>
              Hủy
            </button>
          </div>
        </form>
      )}

      {panel === 'enroll' && !openEnrollment && (
        <form className="card stack narrow" onSubmit={handleEnroll}>
          <h3>Gán vào lớp</h3>
          <label>
            Lớp
            <select
              value={enrollForm.class_id}
              onChange={(e) => setEnrollForm({ ...enrollForm, class_id: e.target.value })}
              required
            >
              <option value="">— Chọn lớp —</option>
              {enrollTargets.map((c) => (
                <option key={c.id} value={c.id}>{c.name} ({c.school_year})</option>
              ))}
            </select>
          </label>
          <label>
            Ngày vào lớp
            <input
              type="date"
              value={enrollForm.joined_at}
              onChange={(e) => setEnrollForm({ ...enrollForm, joined_at: e.target.value })}
              required
            />
          </label>
          <label>
            Phí ưu đãi (₫/tháng, bỏ trống = theo phí lớp)
            <input
              type="number"
              min={0}
              value={enrollForm.custom_monthly_fee}
              onChange={(e) => setEnrollForm({ ...enrollForm, custom_monthly_fee: e.target.value })}
            />
          </label>
          <div className="row-gap">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Đang lưu…' : 'Gán vào lớp'}
            </button>
            <button type="button" className="btn btn-ghost" disabled={submitting} onClick={() => setPanel(null)}>
              Hủy
            </button>
          </div>
        </form>
      )}

      <h3>Lịch sử lớp học</h3>
      <div className="table-wrap table-cards">
        <table>
          <thead>
            <tr>
              <th>Lớp</th>
              <th>Vào lớp</th>
              <th>Rời lớp</th>
              <th>Lý do rời</th>
            </tr>
          </thead>
          <tbody>
            {enrollments.length === 0 && (
              <tr><td colSpan={4} className="muted">Chưa từng vào lớp nào</td></tr>
            )}
            {enrollments.map((en) => (
              <tr key={en.id}>
                <td className="td-title">
                  <Link to={`/classes/${en.class_id}`}>{en.class_name}</Link>
                </td>
                <td data-label="Vào lớp">{fmtDate(en.joined_at)}</td>
                <td data-label="Rời lớp">{fmtDate(en.left_at)}</td>
                <td data-label="Lý do rời">
                  {!en.left_at && <span className="badge badge-ok">Đang học</span>}
                  {en.left_at && (
                    <>
                      {LEAVE_REASON_LABELS[en.leave_reason] || en.leave_reason || '—'}
                      {en.transferred_to_class_id != null && (
                        <>
                          {' → '}
                          {classMap[en.transferred_to_class_id]
                            ? classMap[en.transferred_to_class_id].name
                            : `lớp #${en.transferred_to_class_id}`}
                        </>
                      )}
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
