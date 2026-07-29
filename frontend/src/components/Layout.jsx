import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { ROLE_LABELS, useAuth } from '../auth.jsx';

// Icon inline (stroke currentColor) — khong them thu vien icon
const I = {
  home: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 10.5 12 3l9 7.5" /><path d="M5 9.5V21h14V9.5" /></svg>,
  users: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><circle cx="9" cy="8" r="3.5" /><path d="M2.5 20c.8-3.2 3.4-5 6.5-5s5.7 1.8 6.5 5" /><circle cx="17" cy="9" r="2.5" /><path d="M16 15.2c2.4.3 4.4 1.8 5 4.8" /></svg>,
  logs: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M6 3h9l3 3v15H6z" /><path d="M14 3v4h4M9 11h6M9 15h6" /></svg>,
  class: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="4" width="18" height="14" rx="2" /><path d="M8 21h8M12 18v3" /></svg>,
  student: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M2 8.5 12 4l10 4.5L12 13 2 8.5Z" /><path d="M6.5 10.5V16c0 1.5 2.5 3 5.5 3s5.5-1.5 5.5-3v-5.5" /></svg>,
  check: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="4" y="5" width="16" height="16" rx="2" /><path d="M8 3v4M16 3v4M8.5 13.5l2.5 2.5 4.5-4.5" /></svg>,
  grade: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 20V10M10 20V4M16 20v-7M21 20H3" /></svg>,
  money: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><rect x="2.5" y="6" width="19" height="12" rx="2" /><circle cx="12" cy="12" r="2.8" /><path d="M6 9.5v0M18 14.5v0" /></svg>,
  report: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="8.5" /><path d="M12 3.5V12l6 4" /></svg>,
  key: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="8" cy="14" r="4.5" /><path d="M11.5 10.5 20 2M15 7l3 3M17.5 4.5l3 3" /></svg>,
  logout: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h8" /><path d="M10 12h11M17 8l4 4-4 4" /></svg>,
  more: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><circle cx="5" cy="12" r="1.6" /><circle cx="12" cy="12" r="1.6" /><circle cx="19" cy="12" r="1.6" /></svg>,
};

// Sidebar desktop: day du. Bottom nav mobile: 4 muc hay dung nhat + "Thêm".
const MODULES = [
  { to: '/classes', label: 'Lớp học', icon: I.class },
  { to: '/students', label: 'Học sinh', icon: I.student },
  { to: '/attendance', label: 'Điểm danh', icon: I.check },
  { to: '/grades', label: 'Bảng điểm', icon: I.grade },
  { to: '/fees', label: 'Học phí', icon: I.money },
  { to: '/reports', label: 'Báo cáo', icon: I.report },
];

const BOTTOM_NAV = [
  { to: '/', label: 'Tổng quan', icon: I.home, end: true },
  { to: '/attendance', label: 'Điểm danh', icon: I.check },
  { to: '/grades', label: 'Bảng điểm', icon: I.grade },
  { to: '/fees', label: 'Học phí', icon: I.money },
];

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [sheetOpen, setSheetOpen] = useState(false);

  function handleLogout() {
    setSheetOpen(false);
    logout();
    navigate('/login', { replace: true });
  }

  function closeSheet() {
    setSheetOpen(false);
  }

  return (
    <div className="shell">
      {/* ===== Desktop sidebar ===== */}
      <aside className="sidebar">
        <div className="brand"><span className="brand-badge">L</span>Lớp học thêm</div>
        <nav>
          <NavLink to="/" end>{I.home}Tổng quan</NavLink>
          {user.role === 'admin' && <NavLink to="/users">{I.users}Tài khoản</NavLink>}
          {user.role === 'admin' && <NavLink to="/logs">{I.logs}Nhật ký</NavLink>}
          {MODULES.map(({ to, label, icon }) => (
            <NavLink key={to} to={to}>{icon}{label}</NavLink>
          ))}
          <NavLink to="/doi-mat-khau">{I.key}Đổi mật khẩu</NavLink>
        </nav>
        <div className="sidebar-footer">
          <div className="me">
            <div className="me-name">{user.full_name}</div>
            <div className="me-role">{ROLE_LABELS[user.role] || user.role}</div>
          </div>
          <button type="button" className="btn btn-ghost" onClick={handleLogout}>
            Đăng xuất
          </button>
        </div>
      </aside>

      {/* ===== Mobile topbar ===== */}
      <header className="topbar">
        <div className="brand"><span className="brand-badge">L</span>Lớp học thêm</div>
        <div className="topbar-user">
          {user.full_name}
          <br />
          <span style={{ opacity: 0.7 }}>{ROLE_LABELS[user.role] || user.role}</span>
        </div>
      </header>

      <main className="content">
        <Outlet />
      </main>

      {/* ===== Mobile bottom nav ===== */}
      <nav className="bottomnav">
        {BOTTOM_NAV.map(({ to, label, icon, end }) => (
          <NavLink key={to} to={to} end={end}
            className={({ isActive }) => `bn-item${isActive ? ' active' : ''}`}>
            {icon}{label}
          </NavLink>
        ))}
        <button type="button" className={`bn-item${sheetOpen ? ' active' : ''}`}
          onClick={() => setSheetOpen(true)}>
          {I.more}Thêm
        </button>
      </nav>

      {/* ===== Sheet menu (mobile "Thêm") ===== */}
      {sheetOpen && (
        <>
          <div className="sheet-backdrop" onClick={closeSheet} />
          <div className="sheet" role="dialog" aria-label="Menu">
            <div className="sheet-handle" />
            <nav>
              <NavLink to="/classes" onClick={closeSheet}>{I.class}Lớp học</NavLink>
              <NavLink to="/students" onClick={closeSheet}>{I.student}Học sinh</NavLink>
              <NavLink to="/reports" onClick={closeSheet}>{I.report}Báo cáo</NavLink>
              {user.role === 'admin' && (
                <NavLink to="/users" onClick={closeSheet}>{I.users}Tài khoản</NavLink>
              )}
              <NavLink to="/doi-mat-khau" onClick={closeSheet}>{I.key}Đổi mật khẩu</NavLink>
              {user.role === 'admin' && (
                <NavLink to="/logs" onClick={closeSheet}>{I.logs}Nhật ký</NavLink>
              )}
              <button type="button" onClick={handleLogout}>{I.logout}Đăng xuất</button>
            </nav>
          </div>
        </>
      )}
    </div>
  );
}
