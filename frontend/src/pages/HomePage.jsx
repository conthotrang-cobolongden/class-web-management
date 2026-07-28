import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Bar,
  BarChart,
  CartesianGrid,
  LabelList,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { api } from '../api.js';
import { ROLE_LABELS, useAuth } from '../auth.jsx';
import { CLASS_STATUS_LABELS, fmtMoney } from '../format.js';
import {
  SERIES,
  INK,
  gridProps,
  axisProps,
  tooltipStyle,
  fmtCompactMoney,
} from '../charts/theme.js';
import './home.css';

const GRADES = [6, 7, 8, 9];

// 6 thang gan nhat tinh lui tu thang hien tai: [{year, month, label: "T7/26"}, ...]
function lastSixMonths() {
  const out = [];
  const now = new Date();
  let y = now.getFullYear();
  let m = now.getMonth() + 1;
  for (let i = 0; i < 6; i++) {
    out.unshift({ year: y, month: m, label: `T${m}/${String(y).slice(2)}` });
    m -= 1;
    if (m === 0) {
      m = 12;
      y -= 1;
    }
  }
  return out;
}

// Chu tren legend luon mau ink, khong bao gio mau series (luat dataviz)
const legendInk = (value) => (
  <span style={{ color: INK.secondary, fontSize: 13 }}>{value}</span>
);

export default function HomePage() {
  const { user } = useAuth();
  const isAdmin = user.role === 'admin';

  const [dash, setDash] = useState(null); // admin: du lieu dashboard
  const [classes, setClasses] = useState([]); // GV/TG: lop cua minh
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const reqIdRef = useRef(0);

  // Chart doanh thu 6 thang (chi admin)
  const [revenue, setRevenue] = useState(null); // [{label, "Phải thu", "Đã thu"}]
  const [revError, setRevError] = useState('');
  const revReqIdRef = useRef(0);

  useEffect(() => {
    const reqId = ++reqIdRef.current;
    setLoading(true);
    setError('');
    const promise = isAdmin
      ? api('/reports/dashboard')
      : api('/classes?page=1&page_size=100'); // backend tu loc: GV/TG chi thay lop minh
    promise
      .then((data) => {
        if (reqId !== reqIdRef.current) return; // response cu ve muon -> bo
        if (isAdmin) setDash(data);
        else setClasses(data.items);
      })
      .catch((err) => {
        if (reqId === reqIdRef.current) setError(err.message);
      })
      .finally(() => {
        if (reqId === reqIdRef.current) setLoading(false);
      });
  }, [isAdmin]);

  // Doanh thu 6 thang gan nhat: 6 request song song, co race-guard rieng
  useEffect(() => {
    if (!isAdmin) return;
    const reqId = ++revReqIdRef.current;
    setRevenue(null);
    setRevError('');
    const months = lastSixMonths();
    Promise.all(
      months.map(({ year, month }) => api(`/reports/revenue?year=${year}&month=${month}`))
    )
      .then((results) => {
        if (reqId !== revReqIdRef.current) return;
        setRevenue(
          months.map((mo, i) => ({
            label: mo.label,
            'Phải thu': Number(results[i].grand_total_due || 0),
            'Đã thu': Number(results[i].grand_total_paid || 0),
          }))
        );
      })
      .catch((err) => {
        if (reqId === revReqIdRef.current) setRevError(err.message);
      });
  }, [isAdmin]);

  const byGrade = (dash && dash.classes_by_grade) || {};
  const totalClasses = GRADES.reduce((sum, g) => sum + Number(byGrade[g] || 0), 0);
  const gradeData = GRADES.map((g) => ({
    label: `Khối ${g}`,
    'Số lớp': Number(byGrade[g] || 0),
  }));

  return (
    <div>
      <div className="page-head">
        <h2>Tổng quan</h2>
      </div>
      <p>
        Xin chào <strong>{user.full_name}</strong> ({ROLE_LABELS[user.role] || user.role}).
      </p>

      {error && <div className="alert">{error}</div>}

      {/* ===== Skeleton khi dang tai ===== */}
      {loading &&
        (isAdmin ? (
          <>
            <div className="stat-grid">
              {[0, 1, 2, 3, 4].map((i) => (
                <div className="card" key={i}>
                  <div className="skeleton sk-label" />
                  <div className="skeleton sk-num" />
                </div>
              ))}
            </div>
            <div className="chart-grid">
              {[0, 1].map((i) => (
                <div className="card chart-card" key={i}>
                  <div className="skeleton sk-title" />
                  <div className="skeleton sk-chart" />
                </div>
              ))}
            </div>
          </>
        ) : (
          <div className="class-grid">
            {[0, 1, 2].map((i) => (
              <div className="card" key={i}>
                <div className="skeleton sk-title" />
                <div className="skeleton sk-line" />
                <div className="skeleton sk-line-short" />
                <div className="skeleton sk-btn" />
              </div>
            ))}
          </div>
        ))}

      {/* ===== Admin: dashboard toan trung tam (FR-7.4) ===== */}
      {isAdmin && !loading && dash && (
        <>
          <div className="stat-grid">
            <div className="card">
              <div className="stat-label">Học sinh đang học</div>
              <div className="stat-num">{dash.active_students}</div>
            </div>
            <div className="card">
              <div className="stat-label">Lớp đang mở</div>
              <div className="stat-num">{totalClasses}</div>
              <div className="stat-sub">
                {GRADES.map((g) => `Khối ${g}: ${byGrade[g] || 0}`).join(' · ')}
              </div>
            </div>
            <div className="card">
              <div className="stat-label">Học sinh mới tháng này</div>
              <div className="stat-num">{dash.new_students_this_month}</div>
            </div>
            <div className="card">
              <div className="stat-label">Học sinh đã nghỉ</div>
              <div className="stat-num">{dash.left_students}</div>
            </div>
            <div className="card">
              <div className="stat-label">Khoản phí chưa đóng tháng này</div>
              <div className="stat-num">{dash.unpaid_fees_this_month}</div>
            </div>
          </div>

          <div className="chart-grid">
            {/* Chart 1: grouped bar 2 series, co legend (>=2 series) */}
            <div className="card chart-card">
              <h3>Doanh thu 6 tháng gần nhất</h3>
              <p className="chart-sub">Tổng phải thu và đã thu toàn trung tâm theo tháng</p>
              {revError && (
                <p className="muted">Không tải được dữ liệu doanh thu: {revError}</p>
              )}
              {!revError && !revenue && <div className="skeleton sk-chart" />}
              {revenue && (
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={revenue} margin={{ top: 8, right: 8, left: 0, bottom: 0 }} barGap={2}>
                    <CartesianGrid {...gridProps} />
                    <XAxis dataKey="label" {...axisProps} />
                    <YAxis {...axisProps} tickFormatter={fmtCompactMoney} width={46} />
                    <Tooltip
                      {...tooltipStyle}
                      itemStyle={{ color: INK.primary }}
                      formatter={(v) => fmtMoney(v)}
                    />
                    <Legend formatter={legendInk} iconType="circle" iconSize={9} />
                    <Bar dataKey="Phải thu" fill={SERIES[0]} radius={[4, 4, 0, 0]} maxBarSize={36} />
                    <Bar dataKey="Đã thu" fill={SERIES[1]} radius={[4, 4, 0, 0]} maxBarSize={36} />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>

            {/* Chart 2: 1 series -> khong legend, tieu de tu noi; label so tren cot */}
            <div className="card chart-card">
              <h3>Lớp học theo khối</h3>
              <p className="chart-sub">Số lớp đang mở ở từng khối</p>
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={gradeData} margin={{ top: 16, right: 8, left: 0, bottom: 0 }}>
                  <CartesianGrid {...gridProps} />
                  <XAxis dataKey="label" {...axisProps} />
                  <YAxis {...axisProps} allowDecimals={false} width={30} />
                  <Tooltip {...tooltipStyle} itemStyle={{ color: INK.primary }} />
                  <Bar dataKey="Số lớp" fill={SERIES[0]} radius={[4, 4, 0, 0]} maxBarSize={48}>
                    <LabelList dataKey="Số lớp" position="top" fill={INK.secondary} fontSize={12} />
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>

          <p>
            <Link to="/reports">Xem báo cáo chi tiết →</Link>
          </p>
        </>
      )}

      {/* ===== GV chinh / Tro giang: lop cua minh + loi tat ===== */}
      {!isAdmin && !loading && !error && (
        <>
          <h3>Lớp của bạn ({classes.length})</h3>
          {classes.length === 0 ? (
            <p className="muted">Bạn chưa được gán vào lớp nào. Liên hệ Admin để được phân công.</p>
          ) : (
            <div className="class-grid">
              {classes.map((c) => (
                <div key={c.id} className="card">
                  <div className="class-head">
                    <h3>{c.name}</h3>
                    <span className={`badge ${c.status === 'active' ? 'badge-ok' : 'badge-off'}`}>
                      {CLASS_STATUS_LABELS[c.status] || c.status}
                    </span>
                  </div>
                  <div className="class-meta">
                    Khối {c.grade} · Năm học {c.school_year}
                  </div>
                  <div>
                    <strong>{c.student_count}</strong> học sinh
                    {c.main_teacher && (
                      <span className="muted"> · GV chính: {c.main_teacher.full_name}</span>
                    )}
                  </div>
                  <div className="quick-links">
                    <Link className="btn btn-sm" to="/attendance">Điểm danh</Link>
                    <Link className="btn btn-sm" to="/grades">Nhập điểm</Link>
                    <Link className="btn btn-sm btn-ghost" to={`/classes/${c.id}`}>Chi tiết lớp</Link>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}
