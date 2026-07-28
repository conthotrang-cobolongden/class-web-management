import { useCallback, useEffect, useRef, useState } from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer, LabelList,
} from 'recharts';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { fmtMoney } from '../../format.js';
import { SERIES, INK, gridProps, axisProps, tooltipStyle, fmtCompactMoney } from '../../charts/theme.js';
import './reports.css';

const MONTHS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];

function toISODate(d) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

// attendance_rate/avg_rate co the tra ve dang 0..1 hoac 0..100 -> quy het ve %
function toPercent(r) {
  if (r == null) return null;
  const n = Number(r);
  if (Number.isNaN(n)) return null;
  return n <= 1 ? n * 100 : n;
}

function fmtPercent(pct) {
  if (pct == null) return '—';
  return `${pct.toLocaleString('vi-VN', { maximumFractionDigits: 1 })}%`;
}

function rateClass(pct) {
  if (pct == null) return 'badge-off';
  if (pct >= 90) return 'rate-good';
  if (pct >= 70) return 'rate-warn';
  return 'rate-bad';
}

export default function ReportsPage() {
  const { user } = useAuth();
  // FR-6.5: tro giang KHONG duoc xem doanh thu -> an han tab (backend van chan 403)
  const canSeeRevenue = user.role !== 'assistant';

  const [tab, setTab] = useState('attendance');

  // Danh sach lop cho select (GV/TG: backend tu tra ve chi lop minh)
  const [classes, setClasses] = useState([]);
  const [classesError, setClassesError] = useState('');
  const classesReqRef = useRef(0);

  // --- Tab 1: Chuyen can ---
  const [attClassId, setAttClassId] = useState('');
  const [from, setFrom] = useState(() => {
    const d = new Date();
    return toISODate(new Date(d.getFullYear(), d.getMonth(), 1)); // dau thang nay
  });
  const [to, setTo] = useState(() => toISODate(new Date()));
  const [threshold, setThreshold] = useState('3');
  const [attData, setAttData] = useState(null);
  const [attLoading, setAttLoading] = useState(false);
  const [attError, setAttError] = useState('');
  const attReqRef = useRef(0);

  // --- Tab 2: Doanh thu ---
  const [revYear, setRevYear] = useState(() => String(new Date().getFullYear()));
  const [revMonth, setRevMonth] = useState(() => String(new Date().getMonth() + 1));
  const [revClassId, setRevClassId] = useState('');
  const [revData, setRevData] = useState(null);
  const [revLoading, setRevLoading] = useState(false);
  const [revError, setRevError] = useState('');
  const revReqRef = useRef(0);

  useEffect(() => {
    const reqId = ++classesReqRef.current;
    api('/classes?page=1&page_size=100')
      .then((data) => {
        if (reqId !== classesReqRef.current) return;
        setClasses(data.items);
        // Chi duoc gan dung 1 lop -> chon san cho nhanh (GV dung dien thoai)
        if (data.items.length === 1) setAttClassId(String(data.items[0].id));
      })
      .catch((err) => {
        if (reqId === classesReqRef.current) setClassesError(err.message);
      });
  }, []);

  const invalidRange = Boolean(from && to && from > to);

  const loadAttendance = useCallback(async () => {
    if (!attClassId || invalidRange) return;
    const reqId = ++attReqRef.current;
    setAttLoading(true);
    setAttError('');
    try {
      const params = new URLSearchParams({ class_id: attClassId });
      if (from) params.set('from', from);
      if (to) params.set('to', to);
      const th = parseInt(threshold, 10);
      if (!Number.isNaN(th) && th > 0) params.set('threshold', String(th));
      const data = await api(`/reports/attendance?${params}`);
      if (reqId !== attReqRef.current) return; // response cu ve muon -> bo
      setAttData(data);
    } catch (err) {
      if (reqId === attReqRef.current) {
        setAttError(err.message);
        setAttData(null);
      }
    } finally {
      if (reqId === attReqRef.current) setAttLoading(false);
    }
  }, [attClassId, from, to, threshold, invalidRange]);

  useEffect(() => {
    loadAttendance();
  }, [loadAttendance]);

  const loadRevenue = useCallback(async () => {
    if (!canSeeRevenue) return;
    const y = parseInt(revYear, 10);
    const m = parseInt(revMonth, 10);
    if (!y || !m) return;
    const reqId = ++revReqRef.current;
    setRevLoading(true);
    setRevError('');
    try {
      const params = new URLSearchParams({ year: String(y), month: String(m) });
      if (revClassId) params.set('class_id', revClassId);
      const data = await api(`/reports/revenue?${params}`);
      if (reqId !== revReqRef.current) return;
      setRevData(data);
    } catch (err) {
      if (reqId === revReqRef.current) {
        setRevError(err.message);
        setRevData(null);
      }
    } finally {
      if (reqId === revReqRef.current) setRevLoading(false);
    }
  }, [canSeeRevenue, revYear, revMonth, revClassId]);

  useEffect(() => {
    if (tab === 'revenue') loadRevenue();
  }, [tab, loadRevenue]);

  const thresholdNum = parseInt(threshold, 10) || 3;
  const avgPct = attData ? toPercent(attData.summary && attData.summary.avg_rate) : null;

  // Du lieu chart chuyen can: 1 series ty le % (sap xep giam dan de doc nhanh ai thap nhat)
  const attItems = (attData && attData.items) || [];
  const attChartData = attItems
    .map((s) => ({ name: s.full_name, rate: toPercent(s.attendance_rate) }))
    .sort((a, b) => (b.rate == null ? -1 : b.rate) - (a.rate == null ? -1 : a.rate));
  // >20 HS -> keo cao chart theo so HS de moi hang ~28px van doc duoc
  const attChartHeight = attChartData.length > 20 ? attChartData.length * 28 + 60 : 260;

  // Du lieu chart doanh thu: Phai thu vs Da thu theo lop + tong so khoan theo trang thai
  const revItems = (revData && revData.items) || [];
  const revChartData = revItems.map((r) => ({
    name: r.class_name,
    due: Number(r.total_due) || 0,
    paid: Number(r.total_paid) || 0,
  }));
  const feeTotals = revItems.reduce(
    (acc, r) => ({
      paid: acc.paid + (Number(r.paid_count) || 0),
      partial: acc.partial + (Number(r.partial_count) || 0),
      unpaid: acc.unpaid + (Number(r.unpaid_count) || 0),
    }),
    { paid: 0, partial: 0, unpaid: 0 },
  );

  return (
    <div>
      <div className="page-head">
        <h2>Báo cáo</h2>
      </div>

      <div className="tabs">
        <button
          type="button"
          className={`tab-btn ${tab === 'attendance' ? 'active' : ''}`}
          onClick={() => setTab('attendance')}
        >
          Chuyên cần
        </button>
        {canSeeRevenue && (
          <button
            type="button"
            className={`tab-btn ${tab === 'revenue' ? 'active' : ''}`}
            onClick={() => setTab('revenue')}
          >
            Doanh thu
          </button>
        )}
      </div>

      {classesError && <div className="alert">{classesError}</div>}

      {tab === 'attendance' && (
        <div>
          <div className="report-filters">
            <label>
              Lớp
              <select value={attClassId} onChange={(e) => setAttClassId(e.target.value)}>
                <option value="">— Chọn lớp —</option>
                {classes.map((c) => (
                  <option key={c.id} value={c.id}>{c.name} (khối {c.grade})</option>
                ))}
              </select>
            </label>
            <label>
              Từ ngày
              <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
            </label>
            <label>
              Đến ngày
              <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
            </label>
            <label className="filter-narrow">
              Ngưỡng vắng (buổi)
              <input
                type="number"
                min={1}
                value={threshold}
                onChange={(e) => setThreshold(e.target.value)}
              />
            </label>
          </div>

          {invalidRange && <div className="alert">"Từ ngày" phải trước hoặc bằng "Đến ngày"</div>}
          {attError && <div className="alert">{attError}</div>}

          {!attClassId && !attError && (
            <p className="muted">Chọn lớp để xem báo cáo chuyên cần.</p>
          )}

          {attClassId && attLoading && <p className="muted">Đang tải…</p>}

          {attClassId && !attLoading && attData && (
            <>
              <div className="summary-grid">
                <div className="card">
                  <div className="summary-label">Số buổi trong khoảng</div>
                  <div className="summary-num">
                    {attData.summary ? attData.summary.total_sessions : '—'}
                  </div>
                </div>
                <div className="card">
                  <div className="summary-label">Tỷ lệ đi học trung bình</div>
                  <div className="summary-num">
                    <span className={`badge ${rateClass(avgPct)}`}>{fmtPercent(avgPct)}</span>
                  </div>
                </div>
              </div>

              {attChartData.length > 0 && (
                <div className="card chart-card">
                  <h3>Tỷ lệ đi học theo học sinh</h3>
                  <p className="chart-sub">% buổi có mặt trong khoảng đã chọn, xếp từ cao xuống thấp</p>
                  <ResponsiveContainer width="100%" height={attChartHeight}>
                    <BarChart
                      data={attChartData}
                      layout="vertical"
                      margin={{ top: 4, right: 44, bottom: 0, left: 0 }}
                    >
                      <CartesianGrid {...gridProps} vertical horizontal={false} />
                      <XAxis
                        type="number"
                        domain={[0, 100]}
                        ticks={[0, 25, 50, 75, 100]}
                        tickFormatter={(v) => `${v}%`}
                        {...axisProps}
                      />
                      <YAxis
                        type="category"
                        dataKey="name"
                        width={120}
                        interval={0}
                        {...axisProps}
                      />
                      <Tooltip
                        {...tooltipStyle}
                        itemStyle={{ color: INK.primary }}
                        formatter={(value) => [fmtPercent(value), 'Tỷ lệ đi học']}
                      />
                      <Bar dataKey="rate" name="Tỷ lệ đi học" fill={SERIES[0]} radius={[0, 4, 4, 0]} maxBarSize={20}>
                        <LabelList
                          dataKey="rate"
                          position="right"
                          fill={INK.secondary}
                          fontSize={11}
                          formatter={(v) => (v == null ? '' : `${Math.round(v)}%`)}
                        />
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}

              <div className="table-wrap table-cards">
                <table>
                  <thead>
                    <tr>
                      <th>Họ tên</th>
                      <th className="num">Có mặt</th>
                      <th className="num">Vắng</th>
                      <th className="num">Muộn</th>
                      <th className="num">Có phép</th>
                      <th className="num">Tổng buổi</th>
                      <th>Tỷ lệ đi học</th>
                    </tr>
                  </thead>
                  <tbody>
                    {attItems.length === 0 && (
                      <tr><td colSpan={7} className="muted">Không có dữ liệu trong khoảng này</td></tr>
                    )}
                    {attItems.map((s) => {
                      const pct = toPercent(s.attendance_rate);
                      return (
                        <tr key={s.student_id}>
                          <td className="td-title">{s.full_name}</td>
                          <td className="num" data-label="Có mặt">{s.present}</td>
                          <td className="num" data-label="Vắng">{s.absent}</td>
                          <td className="num" data-label="Muộn">{s.late}</td>
                          <td className="num" data-label="Có phép">{s.excused}</td>
                          <td className="num" data-label="Tổng buổi">{s.total_sessions}</td>
                          <td data-label="Tỷ lệ đi học">
                            <span className={`badge ${rateClass(pct)}`}>{fmtPercent(pct)}</span>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              <div className="card" style={{ marginTop: 18 }}>
                <h3>Học sinh vắng nhiều (≥ {thresholdNum} buổi)</h3>
                {(attData.frequent_absentees || []).length === 0 ? (
                  <p className="muted">
                    Không có học sinh nào vắng từ {thresholdNum} buổi trở lên trong khoảng này.
                  </p>
                ) : (
                  <ul className="absent-list">
                    {attData.frequent_absentees.map((s) => (
                      <li key={s.student_id}>
                        <strong>{s.full_name}</strong> — vắng {s.absent} buổi
                        {s.total_sessions != null && <span className="muted"> / {s.total_sessions} buổi</span>}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </>
          )}
        </div>
      )}

      {tab === 'revenue' && canSeeRevenue && (
        <div>
          <div className="report-filters">
            <label className="filter-narrow">
              Tháng
              <select value={revMonth} onChange={(e) => setRevMonth(e.target.value)}>
                {MONTHS.map((m) => (
                  <option key={m} value={m}>Tháng {m}</option>
                ))}
              </select>
            </label>
            <label className="filter-narrow">
              Năm
              <input
                type="number"
                min={2020}
                max={2100}
                value={revYear}
                onChange={(e) => setRevYear(e.target.value)}
              />
            </label>
            <label>
              Lớp
              <select value={revClassId} onChange={(e) => setRevClassId(e.target.value)}>
                <option value="">
                  {user.role === 'admin' ? 'Tất cả lớp' : 'Tất cả lớp của tôi'}
                </option>
                {classes.map((c) => (
                  <option key={c.id} value={c.id}>{c.name} (khối {c.grade})</option>
                ))}
              </select>
            </label>
          </div>

          {revError && <div className="alert">{revError}</div>}
          {revLoading && <p className="muted">Đang tải…</p>}

          {!revLoading && revData && (
            <>
              <div className="summary-grid">
                <div className="card">
                  <div className="summary-label">Tổng phải thu</div>
                  <div className="summary-num">{fmtMoney(revData.grand_total_due)}</div>
                </div>
                <div className="card">
                  <div className="summary-label">Tổng đã thu</div>
                  <div className="summary-num">{fmtMoney(revData.grand_total_paid)}</div>
                </div>
              </div>

              {revItems.length > 0 && (
                <div className="fee-status-row">
                  <span className="badge badge-ok">Đã đóng: {feeTotals.paid} khoản</span>
                  <span className="badge badge-warn">Một phần: {feeTotals.partial}</span>
                  <span className="badge badge-danger">Chưa đóng: {feeTotals.unpaid}</span>
                </div>
              )}

              {revChartData.length > 0 && (
                <div className="card chart-card">
                  <h3>Phải thu vs Đã thu theo lớp</h3>
                  <p className="chart-sub">Tháng {revMonth}/{revYear}</p>
                  <ResponsiveContainer width="100%" height={260}>
                    <BarChart data={revChartData} margin={{ top: 4, right: 8, bottom: 0, left: 8 }}>
                      <CartesianGrid {...gridProps} />
                      <XAxis dataKey="name" {...axisProps} />
                      <YAxis width={52} tickFormatter={fmtCompactMoney} {...axisProps} />
                      <Tooltip {...tooltipStyle} itemStyle={{ color: INK.primary }} formatter={(value) => fmtMoney(value)} />
                      <Legend
                        iconType="square"
                        iconSize={10}
                        formatter={(value) => (
                          <span style={{ color: INK.secondary, fontSize: 13 }}>{value}</span>
                        )}
                      />
                      <Bar dataKey="due" name="Phải thu" fill={SERIES[0]} radius={[4, 4, 0, 0]} maxBarSize={36} />
                      <Bar dataKey="paid" name="Đã thu" fill={SERIES[1]} radius={[4, 4, 0, 0]} maxBarSize={36} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}

              <div className="table-wrap table-cards">
                <table>
                  <thead>
                    <tr>
                      <th>Lớp</th>
                      <th className="num">Phải thu</th>
                      <th className="num">Đã thu</th>
                      <th className="num">Chưa đóng</th>
                      <th className="num">Đóng một phần</th>
                      <th className="num">Đã đóng</th>
                    </tr>
                  </thead>
                  <tbody>
                    {revItems.length === 0 && (
                      <tr>
                        <td colSpan={6} className="muted">
                          Chưa có khoản phí nào trong tháng {revMonth}/{revYear}
                        </td>
                      </tr>
                    )}
                    {revItems.map((r) => (
                      <tr key={r.class_id}>
                        <td className="td-title">{r.class_name}</td>
                        <td className="num" data-label="Phải thu">{fmtMoney(r.total_due)}</td>
                        <td className="num" data-label="Đã thu">{fmtMoney(r.total_paid)}</td>
                        <td className="num" data-label="Chưa đóng">{r.unpaid_count}</td>
                        <td className="num" data-label="Đóng một phần">{r.partial_count}</td>
                        <td className="num" data-label="Đã đóng">{r.paid_count}</td>
                      </tr>
                    ))}
                  </tbody>
                  {revItems.length > 0 && (
                    <tfoot>
                      <tr>
                        <td className="td-title"><strong>Tổng cộng</strong></td>
                        <td className="num" data-label="Phải thu"><strong>{fmtMoney(revData.grand_total_due)}</strong></td>
                        <td className="num" data-label="Đã thu"><strong>{fmtMoney(revData.grand_total_paid)}</strong></td>
                        <td className="td-spacer" colSpan={3} />
                      </tr>
                    </tfoot>
                  )}
                </table>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
