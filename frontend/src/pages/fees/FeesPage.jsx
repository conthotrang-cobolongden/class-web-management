import { Fragment, useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../api.js';
import { useAuth } from '../../auth.jsx';
import { FEE_STATUS_LABELS, PAYMENT_METHOD_LABELS, fmtDateTime, fmtMoney } from '../../format.js';
import { summaryForRole } from '../../features/fees/feeSummary.js';
import './fees.css';

const PAGE_SIZE = 20;
const FEE_BADGE = { unpaid: 'badge-danger', partial: 'badge-warn', paid: 'badge-ok' };
const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1);

// Gia tri mac dinh cho input datetime-local: gio dia phuong hien tai (YYYY-MM-DDTHH:mm)
function nowLocalInput() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

// Theo doi viewport mobile (khop breakpoint 768px cua styles.css)
function useIsMobile() {
  const [mobile, setMobile] = useState(
    () => typeof window !== 'undefined' && window.matchMedia('(max-width: 768px)').matches,
  );
  useEffect(() => {
    const mq = window.matchMedia('(max-width: 768px)');
    const onChange = (e) => setMobile(e.matches);
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);
  return mobile;
}

export default function FeesPage() {
  const { user: me } = useAuth();
  const isAdmin = me.role === 'admin';
  const isAssistant = me.role === 'assistant';
  const isMobile = useIsMobile();
  const now = new Date();

  // Bo loc
  const [classes, setClasses] = useState([]);
  const [classesLoaded, setClassesLoaded] = useState(false);
  const [classId, setClassId] = useState(''); // '' = tat ca lop (chi admin duoc phep)
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [year, setYear] = useState(String(now.getFullYear()));
  const [statusFilter, setStatusFilter] = useState('');

  // Danh sach khoan phi
  const [items, setItems] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  // Mo rong xem lich su dong tien
  const [expandedId, setExpandedId] = useState(null);
  const [payments, setPayments] = useState({}); // fee_id -> {loading, error, items}

  // Form ghi nhan dong tien / dieu chinh
  const [paymentForm, setPaymentForm] = useState(null); // {fee, amount, method, paid_at, note}
  const [adjustForm, setAdjustForm] = useState(null); // {fee, adjusted_amount, adjustment_reason}
  const [showFeeTotals, setShowFeeTotals] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [generating, setGenerating] = useState(false);

  const reqIdRef = useRef(0);
  const noticeTimerRef = useRef();

  // Tai danh sach lop cho bo loc (backend tu gioi han: GV/TG chi thay lop minh)
  useEffect(() => {
    let alive = true;
    api('/classes?page_size=100')
      .then((data) => {
        if (!alive) return;
        setClasses(data.items);
        setClassesLoaded(true);
      })
      .catch((err) => {
        if (!alive) return;
        setError(err.message);
        setClassesLoaded(true);
      });
    return () => {
      alive = false;
    };
  }, []);

  // GV/TG bat buoc co class_id -> tu chon lop dau tien
  useEffect(() => {
    if (!isAdmin && !classId && classes.length > 0) setClassId(String(classes[0].id));
  }, [isAdmin, classId, classes]);

  const load = useCallback(async () => {
    if (!isAdmin && !classId) return; // khong phai admin thi phai chon lop truoc
    if (!/^\d{4}$/.test(year)) return; // nam chua hop le (dang go do)
    const reqId = ++reqIdRef.current;
    setLoading(true);
    setError('');
    try {
      const params = new URLSearchParams({
        year,
        month: String(month),
        page: String(page),
        page_size: String(PAGE_SIZE),
      });
      if (classId) params.set('class_id', classId);
      if (statusFilter) params.set('status', statusFilter);
      const data = await api(`/fees?${params}`);
      if (reqId !== reqIdRef.current) return; // response cu ve muon -> bo, tranh de ket qua moi
      // Trang hien tai khong con ton tai (total giam do doi loc) -> keo ve trang cuoi
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
  }, [isAdmin, classId, year, month, statusFilter, page]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => () => clearTimeout(noticeTimerRef.current), []);

  // Sheet mobile dang mo -> khoa cuon nen phia sau
  const sheetOpen = isMobile && Boolean(paymentForm || adjustForm);
  useEffect(() => {
    if (!sheetOpen) return undefined;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = prev;
    };
  }, [sheetOpen]);

  function flash(msg) {
    clearTimeout(noticeTimerRef.current);
    setNotice(msg);
    noticeTimerRef.current = setTimeout(() => setNotice(''), 3000);
  }

  // Doi bo loc -> ve trang 1, dong hang dang mo
  function changeFilter(setter) {
    return (value) => {
      setter(value);
      setPage(1);
      setExpandedId(null);
    };
  }

  const setClassFilter = changeFilter(setClassId);
  const setMonthFilter = changeFilter((v) => setMonth(Number(v)));
  const setYearFilter = changeFilter(setYear);
  const setStatusF = changeFilter(setStatusFilter);

  async function loadPayments(feeId) {
    setPayments((p) => ({ ...p, [feeId]: { loading: true, items: [] } }));
    try {
      const data = await api(`/fees/${feeId}/payments`);
      setPayments((p) => ({ ...p, [feeId]: { loading: false, items: data.items } }));
    } catch (err) {
      setPayments((p) => ({ ...p, [feeId]: { loading: false, error: err.message, items: [] } }));
    }
  }

  function toggleExpand(fee) {
    if (expandedId === fee.id) {
      setExpandedId(null);
      return;
    }
    setExpandedId(fee.id);
    loadPayments(fee.id); // luon tai lai de co du lieu moi nhat
  }

  function openPayment(fee) {
    setAdjustForm(null);
    setPaymentForm({ fee, amount: '', method: 'cash', paid_at: nowLocalInput(), note: '' });
    if (!isMobile) window.scrollTo({ top: 0, behavior: 'smooth' }); // mobile dung sheet, khong can cuon
  }

  function openAdjust(fee) {
    setPaymentForm(null);
    setAdjustForm({
      fee,
      adjusted_amount: String(fee.adjusted_amount ?? fee.base_amount ?? ''),
      adjustment_reason: '',
    });
    if (!isMobile) window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function closeForms() {
    if (submitting) return;
    setPaymentForm(null);
    setAdjustForm(null);
  }

  async function handlePaymentSubmit(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const body = {
        amount: Number(paymentForm.amount),
        method: paymentForm.method,
        paid_at: new Date(paymentForm.paid_at).toISOString(),
      };
      if (paymentForm.note.trim()) body.note = paymentForm.note.trim();
      const feeId = paymentForm.fee.id;
      await api(`/fees/${feeId}/payments`, { method: 'POST', body });
      setPaymentForm(null);
      flash('Đã ghi nhận đóng tiền');
      load();
      if (expandedId === feeId) loadPayments(feeId); // dang mo lich su -> tai lai luon
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleAdjustSubmit(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await api(`/fees/${adjustForm.fee.id}/adjust`, {
        method: 'PUT',
        body: {
          adjusted_amount: Number(adjustForm.adjusted_amount),
          adjustment_reason: adjustForm.adjustment_reason.trim(),
        },
      });
      setAdjustForm(null);
      flash('Đã điều chỉnh khoản phí');
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleGenerate() {
    if (!/^\d{4}$/.test(year)) {
      setError('Năm không hợp lệ');
      return;
    }
    if (!window.confirm(`Sinh khoản phí tháng ${month}/${year} cho tất cả học sinh đang học?`)) return;
    setError('');
    setGenerating(true);
    try {
      const data = await api('/fees/generate', {
        method: 'POST',
        body: { year: Number(year), month: Number(month) },
      });
      flash(`Đã sinh ${data.created} khoản phí, bỏ qua ${data.skipped} đã tồn tại`);
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setGenerating(false);
    }
  }

  // manager = admin HOAC GV chinh cua lop dang chon (backend van la chot chan that)
  const selectedClass = classes.find((c) => String(c.id) === String(classId));
  const isManager =
    isAdmin || Boolean(selectedClass && selectedClass.main_teacher && selectedClass.main_teacher.id === me.id);

  const showClassCol = !classId; // admin xem "Tat ca lop" thi can cot Lop
  const colCount = showClassCol ? 6 : 5;
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const noClass = classesLoaded && !isAdmin && classes.length === 0;

  const feeSummary = summaryForRole(me.role, items);
  const statNumCls = loading ? 'stat-num skeleton' : 'stat-num';

  // Form dung chung cho ca card (desktop) va sheet (mobile)
  function renderPaymentForm(inSheet) {
    return (
      <form className={inSheet ? 'stack fee-sheet-form' : 'card stack'} onSubmit={handlePaymentSubmit}>
        <h3>
          Ghi nhận đóng tiền — {paymentForm.fee.student_name} (tháng {paymentForm.fee.fee_month}/
          {paymentForm.fee.fee_year})
        </h3>
        <div className="muted">
          Phải đóng: {fmtMoney(paymentForm.fee.amount_due)} · Đã đóng: {fmtMoney(paymentForm.fee.paid_total)}
        </div>
        <div className="grid-2">
          <label>
            Số tiền (VND)
            <input
              type="number"
              min={1}
              required
              autoFocus
              value={paymentForm.amount}
              onChange={(e) => setPaymentForm({ ...paymentForm, amount: e.target.value })}
            />
          </label>
          <label>
            Hình thức
            <select
              value={paymentForm.method}
              onChange={(e) => setPaymentForm({ ...paymentForm, method: e.target.value })}
            >
              {Object.entries(PAYMENT_METHOD_LABELS).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </label>
          <label>
            Ngày giờ đóng
            <input
              type="datetime-local"
              required
              value={paymentForm.paid_at}
              onChange={(e) => setPaymentForm({ ...paymentForm, paid_at: e.target.value })}
            />
          </label>
          <label>
            Ghi chú
            <input
              value={paymentForm.note}
              onChange={(e) => setPaymentForm({ ...paymentForm, note: e.target.value })}
            />
          </label>
        </div>
        <div className="row-gap">
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Đang lưu…' : 'Lưu'}
          </button>
          <button type="button" className="btn btn-ghost" disabled={submitting} onClick={() => setPaymentForm(null)}>
            Hủy
          </button>
        </div>
      </form>
    );
  }

  function renderAdjustForm(inSheet) {
    return (
      <form className={inSheet ? 'stack fee-sheet-form' : 'card stack'} onSubmit={handleAdjustSubmit}>
        <h3>
          Điều chỉnh khoản phí — {adjustForm.fee.student_name} (tháng {adjustForm.fee.fee_month}/
          {adjustForm.fee.fee_year})
        </h3>
        <div className="muted">Số tiền gốc: {fmtMoney(adjustForm.fee.base_amount)}</div>
        <div className="grid-2">
          <label>
            Số tiền sau điều chỉnh (VND)
            <input
              type="number"
              min={0}
              required
              autoFocus
              value={adjustForm.adjusted_amount}
              onChange={(e) => setAdjustForm({ ...adjustForm, adjusted_amount: e.target.value })}
            />
          </label>
          <label>
            Lý do điều chỉnh (bắt buộc)
            <input
              required
              value={adjustForm.adjustment_reason}
              onChange={(e) => setAdjustForm({ ...adjustForm, adjustment_reason: e.target.value })}
              placeholder="VD: tháng có buổi hủy, tạm nghỉ giữa tháng…"
            />
          </label>
        </div>
        <div className="row-gap">
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Đang lưu…' : 'Lưu điều chỉnh'}
          </button>
          <button type="button" className="btn btn-ghost" disabled={submitting} onClick={() => setAdjustForm(null)}>
            Hủy
          </button>
        </div>
      </form>
    );
  }

  return (
    <div>
      <div className="page-head">
        <h2>Học phí ({total})</h2>
        {isAdmin && (
          <button type="button" className="btn btn-primary" disabled={generating} onClick={handleGenerate}>
            {generating ? 'Đang sinh…' : `Sinh phí tháng ${month}/${year}`}
          </button>
        )}
      </div>

      {error && <div className="alert">{error}</div>}
      {notice && <div className="alert alert-ok">{notice}</div>}

      {/* Desktop: form dang card ngay tren dau nhu cu */}
      {!isMobile && paymentForm && renderPaymentForm(false)}
      {!isMobile && adjustForm && renderAdjustForm(false)}

      {noClass ? (
        <div className="card muted">Bạn chưa được gán vào lớp nào nên không có dữ liệu học phí.</div>
      ) : (
        <>
          <div className="filters">
            <select value={classId} onChange={(e) => setClassFilter(e.target.value)}>
              {isAdmin && <option value="">Tất cả lớp</option>}
              {classes.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
            <select value={month} onChange={(e) => setMonthFilter(e.target.value)}>
              {MONTHS.map((m) => (
                <option key={m} value={m}>Tháng {m}</option>
              ))}
            </select>
            <input
              type="number"
              min={2000}
              max={2100}
              value={year}
              onChange={(e) => setYearFilter(e.target.value)}
              aria-label="Năm"
            />
            <select value={statusFilter} onChange={(e) => setStatusF(e.target.value)}>
              <option value="">Tất cả trạng thái</option>
              {Object.entries(FEE_STATUS_LABELS).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>

          {isAssistant ? (
            <div className="stat-grid fee-stats">
              <div className="card">
                <div className="stat-label">Học sinh chưa đóng</div>
                <div className={statNumCls}>{feeSummary.owingCount}</div>
                <div className="stat-sub">Trong trang hiện tại</div>
              </div>
            </div>
          ) : (
            <div className="stat-grid fee-stats">
              <div className="card">
                <div className="fee-card-head">
                  <div className="stat-label">Tổng phải thu</div>
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm fee-toggle-btn"
                    onClick={() => setShowFeeTotals((show) => !show)}
                  >
                    {showFeeTotals ? 'Ẩn số' : 'Hiện số'}
                  </button>
                </div>
                <div className={statNumCls}>
                  {showFeeTotals ? fmtMoney(feeSummary.due) : '***'}
                </div>
                <div className="stat-sub">
                  {showFeeTotals ? `${items.length} khoản · trang hiện tại` : 'Nhấn Hiện số để xem giá trị'}
                </div>
              </div>
              <div className="card">
                <div className="stat-label">Đã thu</div>
                <div className={statNumCls}>
                  {showFeeTotals ? fmtMoney(feeSummary.paid) : '***'}
                </div>
                <div className="stat-sub">
                  {showFeeTotals ? `${feeSummary.paidPct}% tổng phải thu` : 'Ẩn theo tổng'}
                </div>
              </div>
              <div className="card">
                <div className="stat-label">Còn thiếu</div>
                <div className={statNumCls}>
                  {showFeeTotals ? fmtMoney(feeSummary.left) : '***'}
                </div>
                <div className="stat-sub">{showFeeTotals ? `${feeSummary.owingCount} khoản chưa đóng đủ` : 'Ẩn theo tổng'}</div>
              </div>
            </div>
          )}

          <div className="table-wrap table-cards">
            <table>
              <thead>
                <tr>
                  <th>Học sinh</th>
                  {showClassCol && <th>Lớp</th>}
                  <th className="num">Phải đóng</th>
                  <th className="num">Đã đóng</th>
                  <th>Trạng thái</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {loading && (
                  <tr><td colSpan={colCount} className="muted">Đang tải…</td></tr>
                )}
                {!loading && items.length === 0 && (
                  <tr>
                    <td colSpan={colCount} className="muted">
                      Chưa có khoản phí nào cho tháng {month}/{year}
                      {isAdmin ? ' — bấm "Sinh phí tháng" để tạo' : ''}
                    </td>
                  </tr>
                )}
                {!loading && items.map((f) => {
                  const pay = payments[f.id];
                  return (
                    <Fragment key={f.id}>
                      <tr
                        className="fee-row"
                        title="Bấm để xem lịch sử đóng tiền"
                        onClick={() => toggleExpand(f)}
                      >
                        <td className="td-title">{f.student_name}</td>
                        {showClassCol && <td data-label="Lớp">{f.class_name}</td>}
                        <td className="num" data-label="Phải đóng">
                          <div className="fee-amount">
                            {fmtMoney(f.amount_due)}
                            {f.adjusted_amount != null && (
                              <div className="fee-sub">
                                Gốc {fmtMoney(f.base_amount)} — {f.adjustment_reason || 'đã điều chỉnh'}
                              </div>
                            )}
                          </div>
                        </td>
                        <td className="num" data-label="Đã đóng">{fmtMoney(f.paid_total)}</td>
                        <td data-label="Trạng thái">
                          <span className={`badge ${FEE_BADGE[f.status] || 'badge-off'}`}>
                            {FEE_STATUS_LABELS[f.status] || f.status}
                          </span>
                        </td>
                        <td className="row-gap td-actions">
                          <button
                            type="button"
                            className="btn btn-sm"
                            onClick={(e) => {
                              e.stopPropagation();
                              openPayment(f);
                            }}
                          >
                            Đóng tiền
                          </button>
                          {isManager && (
                            <button
                              type="button"
                              className="btn btn-sm btn-ghost"
                              onClick={(e) => {
                                e.stopPropagation();
                                openAdjust(f);
                              }}
                            >
                              Điều chỉnh
                            </button>
                          )}
                        </td>
                      </tr>
                      {expandedId === f.id && (
                        <tr className="payments-row">
                          <td colSpan={colCount}>
                            <div className="payments-title">Lịch sử đóng tiền — {f.student_name}</div>
                            {pay?.loading && <div className="muted">Đang tải…</div>}
                            {pay?.error && <div className="alert">{pay.error}</div>}
                            {pay && !pay.loading && !pay.error && (
                              pay.items.length === 0 ? (
                                <div className="muted">Chưa có lần đóng tiền nào</div>
                              ) : (
                                <table>
                                  <thead>
                                    <tr>
                                      <th className="num">Số tiền</th>
                                      <th>Hình thức</th>
                                      <th>Ngày giờ</th>
                                      <th>Người ghi nhận</th>
                                      <th>Ghi chú</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {pay.items.map((pm) => (
                                      <tr key={pm.id}>
                                        <td className="num" data-label="Số tiền">{fmtMoney(pm.amount)}</td>
                                        <td data-label="Hình thức">{PAYMENT_METHOD_LABELS[pm.method] || pm.method}</td>
                                        <td data-label="Ngày giờ">{fmtDateTime(pm.paid_at)}</td>
                                        <td data-label="Người ghi nhận">{pm.recorded_by_name || '—'}</td>
                                        <td data-label="Ghi chú">{pm.note || '—'}</td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              )
                            )}
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="pager">
              <button type="button" className="btn btn-sm" disabled={page <= 1} onClick={() => setPage(page - 1)}>
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
        </>
      )}

      {/* Mobile: dialog dang bottom-sheet */}
      {sheetOpen && (
        <>
          <div className="sheet-backdrop" onClick={closeForms} />
          <div className="sheet" role="dialog" aria-modal="true" aria-label={paymentForm ? 'Ghi nhận đóng tiền' : 'Điều chỉnh khoản phí'}>
            <div className="sheet-handle" />
            {paymentForm ? renderPaymentForm(true) : renderAdjustForm(true)}
          </div>
        </>
      )}
    </div>
  );
}
