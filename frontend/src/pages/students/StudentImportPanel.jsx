import { useMemo, useState } from 'react';
import { api, downloadApiFile } from '../../api.js';
import './student-import.css';

export default function StudentImportPanel({ classes = [], onImported, onNotice, onError }) {
  const activeClasses = useMemo(
    () => classes.filter((value) => value.status === 'active'),
    [classes],
  );
  const [classId, setClassId] = useState('');
  const [file, setFile] = useState(null);
  const [errors, setErrors] = useState([]);
  const [inputVersion, setInputVersion] = useState(0);
  const [result, setResult] = useState(null);
  const [working, setWorking] = useState('');
  const [localError, setLocalError] = useState('');

  function resetFeedback() {
    setErrors([]);
    setResult(null);
    setLocalError('');
  }

  async function handleDownload() {
    resetFeedback();
    setWorking('download');
    try {
      await downloadApiFile(`/classes/${classId}/students/import-template`);
      if (onNotice) onNotice('Đã tải file mẫu Excel');
    } catch (error) {
      setLocalError(error.message);
      if (onError) onError(error.message);
    } finally {
      setWorking('');
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();
    resetFeedback();
    if (!classId || !file) return;
    const body = new FormData();
    body.append('file', file);
    setWorking('upload');
    try {
      const data = await api(`/classes/${classId}/students/import`, {
        method: 'POST',
        body,
      });
      setResult(data);
      setFile(null);
      setInputVersion((value) => value + 1);
      if (onImported) onImported(data);
    } catch (error) {
      const rowErrors = error.data && Array.isArray(error.data.errors)
        ? error.data.errors
        : [];
      setErrors(rowErrors);
      if (rowErrors.length === 0) {
        setLocalError(error.message);
        if (onError) onError(error.message);
      }
    } finally {
      setWorking('');
    }
  }

  return (
    <section className="card stack student-import" aria-labelledby="student-import-title">
      <div className="student-import__head">
        <div>
          <h3 id="student-import-title">Nhập danh sách học sinh từ Excel</h3>
          <p className="muted">
            Chọn lớp, tải file mẫu rồi tải lên lại. File có lỗi sẽ không ghi bất kỳ học sinh nào.
          </p>
        </div>
        <span className="badge badge-info">.xlsx</span>
      </div>

      <form className="stack" onSubmit={handleSubmit}>
        <div className="student-import__fields">
          <label>
            Lớp nhận học sinh <span aria-hidden="true">*</span>
            <select
              value={classId}
              onChange={(event) => {
                setClassId(event.target.value);
                setFile(null);
                resetFeedback();
                setInputVersion((value) => value + 1);
              }}
              required
            >
              <option value="">— Chọn lớp trước —</option>
              {activeClasses.map((value) => (
                <option key={value.id} value={value.id}>
                  {value.name} ({value.school_year})
                </option>
              ))}
            </select>
          </label>

          <label>
            File Excel <span aria-hidden="true">*</span>
            <input
              key={`${classId}-${inputVersion}`}
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              disabled={!classId || Boolean(working)}
              onChange={(event) => {
                setFile(event.target.files && event.target.files[0]);
                resetFeedback();
              }}
              required
            />
          </label>
        </div>

        <div className="student-import__actions">
          <button
            type="button"
            className="btn"
            disabled={!classId || Boolean(working)}
            onClick={handleDownload}
          >
            {working === 'download' ? 'Đang tạo file…' : 'Tải file mẫu'}
          </button>
          <button
            type="submit"
            className="btn btn-primary"
            disabled={!classId || !file || Boolean(working)}
          >
            {working === 'upload' ? 'Đang kiểm tra và nhập…' : 'Nhập danh sách'}
          </button>
        </div>
      </form>

      {localError && <div className="alert">{localError}</div>}

      {result && (
        <div className="alert alert-ok student-import__result" role="status">
          <strong>Đã nhập {result.imported} học sinh.</strong>
          {result.student_codes && result.student_codes.length > 0 && (
            <span>Mã học sinh: {result.student_codes.join(', ')}</span>
          )}
        </div>
      )}

      {errors.length > 0 && (
        <div className="student-import__errors" role="alert">
          <div className="student-import__error-title">
            <strong>File có {errors.length} lỗi — chưa có dữ liệu nào được nhập.</strong>
          </div>
          <div className="table-wrap table-cards">
            <table>
              <thead>
                <tr>
                  <th>Dòng</th>
                  <th>Cột</th>
                  <th>Lý do</th>
                </tr>
              </thead>
              <tbody>
                {errors.map((error, index) => (
                  <tr key={`${error.row}-${error.column}-${index}`}>
                    <td data-label="Dòng">{error.row}</td>
                    <td data-label="Cột">{error.column}</td>
                    <td data-label="Lý do">{error.reason}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}
