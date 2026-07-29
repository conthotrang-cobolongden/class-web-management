import { useCallback, useEffect, useRef, useState } from 'react';
import { api, getToken } from '../../api.js';
import { fmtDate } from '../../format.js';
import './GradesPage.css';

const EMPTY_COL_FORM = { id: null, name: '', weight: '1', max_score: '10', column_date: '' };

function cellKey(studentId, columnId) {
  return `${studentId}:${columnId}`;
}

function fmtAvg(v) {
  if (v == null) return '—';
  return (Math.round(v * 100) / 100).toFixed(2);
}

export default function GradesPage() {
  const [classes, setClasses] = useState([]);
  const [classId, setClassId] = useState('');
  const [data, setData] = useState(null); // {columns: [...], items: [...]}
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  // edits: "studentId:columnId" -> chuoi dang go trong o input
  const [edits, setEdits] = useState({});
  const [saving, setSaving] = useState(false);
  const [exporting, setExporting] = useState(false);

  // Quan ly cot diem (manager)
  const [showColumns, setShowColumns] = useState(false);
  const [colForm, setColForm] = useState(null); // null = dong; id=null = tao moi
  const [colSubmitting, setColSubmitting] = useState(false);

  const reqIdRef = useRef(0);
  const noticeTimerRef = useRef();

  // Danh sach lop cho dropdown (backend tu loc theo quyen: admin = tat ca, GV/TG = lop minh)
  useEffect(() => {
    let alive = true;
    api('/classes?page_size=100')
      .then((d) => {
        if (alive) setClasses(d.items);
      })
      .catch((err) => {
        if (alive) setError(err.message);
      });
    return () => {
      alive = false;
    };
  }, []);

  const load = useCallback(async () => {
    if (!classId) {
      setData(null);
      return;
    }
    const reqId = ++reqIdRef.current;
    setLoading(true);
    setError('');
    try {
      const d = await api(`/classes/${classId}/grades`);
      if (reqId !== reqIdRef.current) return; // response cu ve muon -> bo
      setData(d);
    } catch (err) {
      if (reqId === reqIdRef.current) {
        setError(err.message);
        setData(null);
      }
    } finally {
      if (reqId === reqIdRef.current) setLoading(false);
    }
  }, [classId]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => () => clearTimeout(noticeTimerRef.current), []);

  function flash(msg) {
    clearTimeout(noticeTimerRef.current);
    setNotice(msg);
    noticeTimerRef.current = setTimeout(() => setNotice(''), 3000);
  }

  const selCls = classes.find((c) => String(c.id) === String(classId));
  // Lop trong dropdown da duoc backend loc theo membership; backend van la chot chan cuoi.
  const canManageColumns = Boolean(selCls);

  // ===== O diem =====

  function cellValue(it, col) {
    const key = cellKey(it.student_id, col.id);
    if (key in edits) return edits[key];
    const orig = it.scores[String(col.id)];
    return orig == null ? '' : String(orig);
  }

  function isChanged(it, col) {
    const key = cellKey(it.student_id, col.id);
    if (!(key in edits)) return false;
    const raw = String(edits[key]).trim();
    if (raw === '') return false;
    const v = Number(raw);
    if (Number.isNaN(v)) return false;
    const orig = it.scores[String(col.id)];
    return orig == null || Number(orig) !== v;
  }

  // Chi gom nhung o thuc su thay doi (de PUT bulk gui dung phan da sua)
  function collectChanges() {
    const changes = [];
    if (!data) return changes;
    for (const it of data.items) {
      for (const col of data.columns) {
        const key = cellKey(it.student_id, col.id);
        if (!(key in edits)) continue;
        const raw = String(edits[key]).trim();
        if (raw === '') continue;
        const v = Number(raw);
        if (Number.isNaN(v)) continue;
        const orig = it.scores[String(col.id)];
        if (orig != null && Number(orig) === v) continue;
        changes.push({ grade_column_id: col.id, student_id: it.student_id, score: v, _name: it.full_name, _col: col });
      }
    }
    return changes;
  }

  const pendingChanges = collectChanges();
  const changedCount = pendingChanges.length;
  useEffect(() => {
    if (changedCount === 0) return undefined;
    const handleBeforeUnload = (event) => event.preventDefault();
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [changedCount]);


  function handleCellBlur(it, col) {
    const key = cellKey(it.student_id, col.id);
    if (!(key in edits)) return;
    // API khong ho tro xoa diem da nhap -> o bi xoa trang thi tra ve gia tri cu
    if (String(edits[key]).trim() === '') {
      const next = { ...edits };
      delete next[key];
      setEdits(next);
    }
  }

  // Diem TB co trong so tinh lai truc tiep theo gia tri dang go (cung cong thuc FR-5.3 voi backend)
  function computeAverage(it) {
    if (!data) return null;
    let sum = 0;
    let wsum = 0;
    for (const col of data.columns) {
      const raw = cellValue(it, col).trim();
      if (raw === '') continue;
      const v = Number(raw);
      const max = Number(col.max_score);
      const w = Number(col.weight);
      if (Number.isNaN(v) || !max || !w) continue;
      sum += (v / max) * 10 * w;
      wsum += w;
    }
    if (!wsum) return null;
    return sum / wsum;
  }

  function handleClassChange(e) {
    const next = e.target.value;
    if (changedCount > 0 && !window.confirm('Có điểm chưa lưu, chuyển lớp sẽ mất các thay đổi. Tiếp tục?')) {
      return;
    }
    setClassId(next);
    setEdits({});
    setData(null);
    setShowColumns(false);
    setColForm(null);
  }

  async function handleSave() {
    const changes = collectChanges();
    if (changes.length === 0) return;
    for (const c of changes) {
      if (c.score < 0 || c.score > Number(c._col.max_score)) {
        setError(`Điểm của ${c._name} ở cột "${c._col.name}" phải trong khoảng 0..${c._col.max_score}`);
        return;
      }
    }
    setError('');
    setSaving(true);
    try {
      const d = await api('/grades', {
        method: 'PUT',
        body: {
          items: changes.map(({ grade_column_id, student_id, score }) => ({ grade_column_id, student_id, score })),
        },
      });
      flash(d.message || 'Đã lưu điểm');
      setEdits({});
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  // ===== Xuat CSV (fetch blob, kem Authorization) =====

  async function handleExport() {
    setError('');
    setExporting(true);
    try {
      let res;
      try {
        res = await fetch(`/api/classes/${classId}/grades/export`, {
          headers: { Authorization: `Bearer ${getToken()}` },
        });
      } catch {
        throw new Error('Không kết nối được máy chủ, vui lòng kiểm tra mạng và thử lại');
      }
      if (!res.ok) {
        let msg = 'Lỗi hệ thống, vui lòng thử lại';
        try {
          const d = await res.json();
          if (d && d.error) msg = d.error;
        } catch {
          // body khong phai JSON -> giu thong bao chung
        }
        throw new Error(msg);
      }
      const blob = await res.blob();
      const cd = res.headers.get('Content-Disposition') || '';
      const m = cd.match(/filename="?([^";]+)"?/);
      const filename = m ? m[1] : `bang-diem-${selCls ? selCls.name : classId}.csv`;
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err.message);
    } finally {
      setExporting(false);
    }
  }

  // ===== Quan ly cot diem (manager) =====

  async function handleColSubmit(e) {
    e.preventDefault();
    const weight = Number(colForm.weight);
    const maxScore = Number(colForm.max_score);
    if (!(weight > 0)) {
      setError('Trọng số phải lớn hơn 0');
      return;
    }
    if (!(maxScore > 0)) {
      setError('Thang điểm phải lớn hơn 0');
      return;
    }
    setError('');
    setColSubmitting(true);
    const body = {
      name: colForm.name.trim(),
      weight,
      max_score: maxScore,
      column_date: colForm.column_date || null,
    };
    try {
      if (colForm.id == null) {
        await api(`/classes/${classId}/grade-columns`, { method: 'POST', body });
        flash('Đã thêm cột điểm');
      } else {
        await api(`/grade-columns/${colForm.id}`, { method: 'PUT', body });
        flash('Đã cập nhật cột điểm');
      }
      setColForm(null);
      load();
    } catch (err) {
      setError(err.message);
    } finally {
      setColSubmitting(false);
    }
  }

  async function handleColDelete(col) {
    if (!window.confirm(`Bạn chắc chắn muốn xóa cột điểm "${col.name}"? Điểm đã nhập được giữ lại để tra cứu.`)) return;
    setError('');
    try {
      const d = await api(`/grade-columns/${col.id}`, { method: 'DELETE' });
      flash(d.message || 'Đã xóa cột điểm');
      if (colForm && colForm.id === col.id) setColForm(null);
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  const colSpan = (data ? data.columns.length : 0) + 2;

  return (
    <div>
      <div className="page-head">
        <h2>Bảng điểm</h2>
        {classId && data && (
          <div className="row-gap">
            {canManageColumns && (
              <button
                type="button"
                className="btn"
                onClick={() => {
                  setShowColumns((v) => !v);
                  setColForm(null);
                }}
              >
                {showColumns ? 'Đóng quản lý cột' : 'Quản lý cột điểm'}
              </button>
            )}
            <button type="button" className="btn" disabled={exporting} onClick={handleExport}>
              {exporting ? 'Đang xuất…' : 'Xuất CSV'}
            </button>
            <button
              type="button"
              className="btn btn-primary"
              disabled={saving || changedCount === 0}
              onClick={handleSave}
            >
              {saving ? 'Đang lưu…' : `Lưu điểm${changedCount ? ` (${changedCount})` : ''}`}
            </button>
          </div>
        )}
      </div>

      {error && <div className="alert">{error}</div>}
      {notice && <div className="alert alert-ok">{notice}</div>}

      <div className="filters">
        <label>
          Lớp
          <select value={classId} onChange={handleClassChange}>
            <option value="">— Chọn lớp —</option>
            {classes.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({c.school_year})
              </option>
            ))}
          </select>
        </label>
      </div>

      {showColumns && canManageColumns && data && (
        <div className="card stack">
          <div className="page-head">
            <h3>Cột điểm ({data.columns.length})</h3>
            <button
              type="button"
              className="btn btn-sm btn-primary"
              onClick={() => setColForm({ ...EMPTY_COL_FORM })}
            >
              + Thêm cột
            </button>
          </div>

          {colForm && (
            <form className="stack" onSubmit={handleColSubmit}>
              <h3>{colForm.id == null ? 'Thêm cột điểm' : `Sửa cột điểm #${colForm.id}`}</h3>
              <div className="grid-2">
                <label>
                  Tên cột
                  <input
                    value={colForm.name}
                    onChange={(e) => setColForm({ ...colForm, name: e.target.value })}
                    placeholder="VD: Kiểm tra giữa kỳ"
                    required
                  />
                </label>
                <label>
                  Trọng số
                  <input
                    type="number"
                    inputMode="decimal"
                    min="0"
                    step="any"
                    value={colForm.weight}
                    onChange={(e) => setColForm({ ...colForm, weight: e.target.value })}
                    required
                  />
                </label>
                <label>
                  Thang điểm
                  <input
                    type="number"
                    inputMode="decimal"
                    min="0"
                    step="any"
                    value={colForm.max_score}
                    onChange={(e) => setColForm({ ...colForm, max_score: e.target.value })}
                    required
                  />
                </label>
                <label>
                  Ngày (không bắt buộc)
                  <input
                    type="date"
                    value={colForm.column_date}
                    onChange={(e) => setColForm({ ...colForm, column_date: e.target.value })}
                  />
                </label>
              </div>
              <div className="row-gap">
                <button type="submit" className="btn btn-primary" disabled={colSubmitting}>
                  {colSubmitting ? 'Đang lưu…' : 'Lưu'}
                </button>
                <button type="button" className="btn btn-ghost" disabled={colSubmitting} onClick={() => setColForm(null)}>
                  Hủy
                </button>
              </div>
            </form>
          )}

          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Tên cột</th>
                  <th>Trọng số</th>
                  <th>Thang điểm</th>
                  <th>Ngày</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {data.columns.length === 0 && (
                  <tr>
                    <td colSpan={5} className="muted">Chưa có cột điểm nào</td>
                  </tr>
                )}
                {data.columns.map((col) => (
                  <tr key={col.id}>
                    <td>{col.name}</td>
                    <td>×{col.weight}</td>
                    <td>{col.max_score}</td>
                    <td>{fmtDate(col.column_date)}</td>
                    <td className="row-gap">
                      <button
                        type="button"
                        className="btn btn-sm"
                        onClick={() =>
                          setColForm({
                            id: col.id,
                            name: col.name,
                            weight: String(col.weight),
                            max_score: String(col.max_score),
                            column_date: col.column_date ? String(col.column_date).slice(0, 10) : '',
                          })
                        }
                      >
                        Sửa
                      </button>
                      <button type="button" className="btn btn-sm btn-ghost" onClick={() => handleColDelete(col)}>
                        Xóa
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {!classId && <p className="muted">Chọn lớp để xem và nhập bảng điểm.</p>}

      {classId && (loading || data) && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Học sinh</th>
                {data &&
                  data.columns.map((col) => (
                    <th key={col.id} className="grade-col-head">
                      {col.name}
                      <span className="muted">
                        ×{col.weight} · /{col.max_score}
                        {col.column_date ? ` · ${fmtDate(col.column_date)}` : ''}
                      </span>
                    </th>
                  ))}
                <th>Điểm TB</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr>
                  <td colSpan={colSpan} className="muted">Đang tải…</td>
                </tr>
              )}
              {!loading && data && data.items.length === 0 && (
                <tr>
                  <td colSpan={colSpan} className="muted">Lớp chưa có học sinh đang học</td>
                </tr>
              )}
              {!loading &&
                data &&
                data.items.map((it) => (
                  <tr key={it.student_id}>
                    <td>{it.full_name}</td>
                    {data.columns.map((col) => (
                      <td key={col.id}>
                        <input
                          type="number"
                          inputMode="decimal"
                          className={`grade-input${isChanged(it, col) ? ' changed' : ''}`}
                          min={0}
                          max={col.max_score}
                          step="any"
                          disabled={saving}
                          value={cellValue(it, col)}
                          onChange={(e) => setEdits({ ...edits, [cellKey(it.student_id, col.id)]: e.target.value })}
                          onBlur={() => handleCellBlur(it, col)}
                        />
                      </td>
                    ))}
                    <td className="grade-avg">{fmtAvg(computeAverage(it))}</td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      )}

      {classId && !loading && data && data.columns.length === 0 && (
        <p className="muted">
          Lớp chưa có cột điểm.
          {canManageColumns ? ' Bấm "Quản lý cột điểm" để thêm cột đầu tiên.' : ''}
        </p>
      )}
    </div>
  );
}
