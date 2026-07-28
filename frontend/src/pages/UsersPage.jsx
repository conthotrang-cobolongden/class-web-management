import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../api.js';
import { ROLE_LABELS, useAuth } from '../auth.jsx';

const PAGE_SIZE = 20;
const EMPTY_FORM = { full_name: '', email: '', phone: '', role: 'teacher', password: '' };

export default function UsersPage() {
  const { user: me, updateUser } = useAuth();
  const [items, setItems] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [roleFilter, setRoleFilter] = useState('');
  const [activeOnly, setActiveOnly] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(false);

  const [showCreate, setShowCreate] = useState(false);
  const [createForm, setCreateForm] = useState(EMPTY_FORM);
  const [editing, setEditing] = useState(null); // {id, full_name, email, phone, role, password}
  const [submitting, setSubmitting] = useState(false);
  const reqIdRef = useRef(0);
  const noticeTimerRef = useRef();

  const load = useCallback(async () => {
    const reqId = ++reqIdRef.current;
    setLoading(true);
    setError('');
    try {
      const params = new URLSearchParams({ page: String(page), page_size: String(PAGE_SIZE) });
      if (roleFilter) params.set('role', roleFilter);
      if (activeOnly) params.set('active', 'true');
      const data = await api(`/users?${params}`);
      if (reqId !== reqIdRef.current) return; // response cu ve muon -> bo, tranh de ket qua moi
      // Trang hien tai khong con ton tai (total giam do loc/vo hieu hoa) -> keo ve trang cuoi
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
  }, [page, roleFilter, activeOnly]);

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
    try {
      await api('/users', { method: 'POST', body: createForm });
      setShowCreate(false);
      setCreateForm(EMPTY_FORM);
      flash('Đã tạo tài khoản');
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleEditSave(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    const body = {
      full_name: editing.full_name,
      email: editing.email,
      phone: editing.phone,
    };
    // Khong gui role khi sua chinh minh (backend cung chan tu doi role)
    if (editing.id !== me.id) body.role = editing.role;
    if (editing.password) body.password = editing.password;
    try {
      const data = await api(`/users/${editing.id}`, { method: 'PUT', body });
      if (editing.id === me.id) {
        // Dong bo lai thong tin ban than trong context/localStorage (ten, email)
        updateUser({
          id: data.user.id,
          full_name: data.user.full_name,
          email: data.user.email,
          role: data.user.role,
        });
      }
      setEditing(null);
      flash('Đã cập nhật tài khoản');
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function toggleActive(u) {
    const action = u.is_active ? 'deactivate' : 'activate';
    const label = u.is_active ? 'vô hiệu hóa' : 'kích hoạt lại';
    if (!window.confirm(`Bạn chắc chắn muốn ${label} tài khoản "${u.full_name}"?`)) return;
    setError('');
    try {
      const data = await api(`/users/${u.id}/${action}`, { method: 'POST' });
      flash(data.message);
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div>
      <div className="page-head">
        <h2>Tài khoản ({total})</h2>
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => {
            setShowCreate((v) => !v);
            setEditing(null);
          }}
        >
          {showCreate ? 'Đóng form' : '+ Tạo tài khoản'}
        </button>
      </div>

      {error && <div className="alert">{error}</div>}
      {notice && <div className="alert alert-ok">{notice}</div>}

      {showCreate && (
        <form className="card stack" onSubmit={handleCreate}>
          <h3>Tạo tài khoản mới</h3>
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
              Email
              <input
                type="email"
                value={createForm.email}
                onChange={(e) => setCreateForm({ ...createForm, email: e.target.value })}
                required
              />
            </label>
            <label>
              Số điện thoại
              <input
                value={createForm.phone}
                onChange={(e) => setCreateForm({ ...createForm, phone: e.target.value })}
              />
            </label>
            <label>
              Vai trò
              <select
                value={createForm.role}
                onChange={(e) => setCreateForm({ ...createForm, role: e.target.value })}
              >
                <option value="admin">Quản trị</option>
                <option value="teacher">GV chính</option>
                <option value="assistant">Trợ giảng</option>
              </select>
            </label>
            <label>
              Mật khẩu ban đầu (tối thiểu 6 ký tự)
              <input
                type="password"
                value={createForm.password}
                onChange={(e) => setCreateForm({ ...createForm, password: e.target.value })}
                minLength={6}
                required
              />
            </label>
          </div>
          <div>
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Đang tạo…' : 'Tạo'}
            </button>
          </div>
        </form>
      )}

      {editing && (
        <form className="card stack" onSubmit={handleEditSave}>
          <h3>Sửa tài khoản #{editing.id}</h3>
          <div className="grid-2">
            <label>
              Họ tên
              <input
                value={editing.full_name}
                onChange={(e) => setEditing({ ...editing, full_name: e.target.value })}
                required
              />
            </label>
            <label>
              Email
              <input
                type="email"
                value={editing.email}
                onChange={(e) => setEditing({ ...editing, email: e.target.value })}
                required
              />
            </label>
            <label>
              Số điện thoại
              <input
                value={editing.phone}
                onChange={(e) => setEditing({ ...editing, phone: e.target.value })}
              />
            </label>
            <label>
              Vai trò
              <select
                value={editing.role}
                disabled={editing.id === me.id}
                title={editing.id === me.id ? 'Không thể tự thay đổi vai trò của chính mình' : undefined}
                onChange={(e) => setEditing({ ...editing, role: e.target.value })}
              >
                <option value="admin">Quản trị</option>
                <option value="teacher">GV chính</option>
                <option value="assistant">Trợ giảng</option>
              </select>
            </label>
            <label>
              Đặt lại mật khẩu (bỏ trống nếu không đổi)
              <input
                type="password"
                value={editing.password}
                onChange={(e) => setEditing({ ...editing, password: e.target.value })}
                minLength={6}
                placeholder="••••••••"
              />
            </label>
          </div>
          <div className="row-gap">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Đang lưu…' : 'Lưu'}
            </button>
            <button type="button" className="btn btn-ghost" disabled={submitting} onClick={() => setEditing(null)}>
              Hủy
            </button>
          </div>
        </form>
      )}

      <div className="filters">
        <select
          value={roleFilter}
          onChange={(e) => {
            setRoleFilter(e.target.value);
            setPage(1);
          }}
        >
          <option value="">Tất cả vai trò</option>
          <option value="admin">Quản trị</option>
          <option value="teacher">GV chính</option>
          <option value="assistant">Trợ giảng</option>
        </select>
        <label className="check">
          <input
            type="checkbox"
            checked={activeOnly}
            onChange={(e) => {
              setActiveOnly(e.target.checked);
              setPage(1);
            }}
          />
          Chỉ tài khoản đang hoạt động
        </label>
      </div>

      <div className="table-wrap table-cards">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Họ tên</th>
              <th>Email</th>
              <th>SĐT</th>
              <th>Vai trò</th>
              <th>Trạng thái</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {loading && [1, 2, 3].map((i) => (
              <tr key={`sk-${i}`}>
                <td colSpan={7}>
                  <div className="skeleton" style={{ height: 18, width: `${95 - i * 18}%` }} />
                </td>
              </tr>
            ))}
            {!loading && items.length === 0 && (
              <tr><td colSpan={7} className="muted">Không có tài khoản nào</td></tr>
            )}
            {!loading && items.map((u) => (
              <tr key={u.id} className={u.is_active ? '' : 'row-inactive'}>
                <td className="num" data-label="ID">{u.id}</td>
                <td className="td-title">
                  {u.full_name}
                  {u.id === me.id && <span className="badge badge-me">bạn</span>}
                </td>
                <td data-label="Email">{u.email}</td>
                <td className="num" data-label="SĐT">{u.phone || '—'}</td>
                <td data-label="Vai trò">{ROLE_LABELS[u.role] || u.role}</td>
                <td data-label="Trạng thái">
                  <span className={`badge ${u.is_active ? 'badge-ok' : 'badge-off'}`}>
                    {u.is_active ? 'Hoạt động' : 'Vô hiệu hóa'}
                  </span>
                </td>
                <td className="row-gap td-actions">
                  <button
                    type="button"
                    className="btn btn-sm"
                    onClick={() => {
                      setEditing({
                        id: u.id,
                        full_name: u.full_name,
                        email: u.email,
                        phone: u.phone || '',
                        role: u.role,
                        password: '',
                      });
                      setShowCreate(false);
                      window.scrollTo({ top: 0, behavior: 'smooth' });
                    }}
                  >
                    Sửa
                  </button>
                  {u.id !== me.id && (
                    <button type="button" className="btn btn-sm btn-ghost" onClick={() => toggleActive(u)}>
                      {u.is_active ? 'Vô hiệu hóa' : 'Kích hoạt'}
                    </button>
                  )}
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
