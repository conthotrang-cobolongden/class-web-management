import { useState } from 'react';
import { api } from '../api.js';

export default function ChangePasswordPage() {
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setMessage('');
    if (newPassword !== confirm) {
      setError('Xác nhận mật khẩu không khớp');
      return;
    }
    setLoading(true);
    try {
      const data = await api('/auth/change-password', {
        method: 'POST',
        body: { old_password: oldPassword, new_password: newPassword },
      });
      setMessage(data.message);
      setOldPassword('');
      setNewPassword('');
      setConfirm('');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="narrow">
      <h2>Đổi mật khẩu</h2>
      {error && <div className="alert">{error}</div>}
      {message && <div className="alert alert-ok">{message}</div>}
      <form onSubmit={handleSubmit} className="stack">
        <label>
          Mật khẩu cũ
          <input
            type="password"
            value={oldPassword}
            onChange={(e) => setOldPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        <label>
          Mật khẩu mới (tối thiểu 6 ký tự)
          <input
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            autoComplete="new-password"
            minLength={6}
            required
          />
        </label>
        <label>
          Nhập lại mật khẩu mới
          <input
            type="password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            autoComplete="new-password"
            minLength={6}
            required
          />
        </label>
        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? 'Đang lưu…' : 'Đổi mật khẩu'}
        </button>
      </form>
    </div>
  );
}
