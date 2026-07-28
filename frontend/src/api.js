// Goi API backend (contract: JSON snake_case, loi dang {error: "..."} tieng Viet)

const TOKEN_KEY = 'cm_token';
const USER_KEY = 'cm_user';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function getSavedUser() {
  const raw = localStorage.getItem(USER_KEY);
  try {
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function saveSession(token, user) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export async function api(path, { method = 'GET', body } = {}) {
  const headers = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let res;
  try {
    res = await fetch(`/api${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    // fetch reject = khong co HTTP response (backend chet / mat mang) — dich sang tieng Viet
    throw new ApiError(0, 'Không kết nối được máy chủ, vui lòng kiểm tra mạng và thử lại');
  }

  let data = null;
  try {
    data = await res.json();
  } catch {
    // body rong (khong xay ra voi API nay, nhung phong thu)
  }

  if (!res.ok) {
    // Token het han/bi vo hieu hoa giua chung -> ve trang login.
    // (401 khi DANG login thi de nguyen cho form hien thong bao)
    if (res.status === 401 && token) {
      clearSession();
      window.location.assign('/login');
    }
    throw new ApiError(res.status, (data && data.error) || 'Lỗi hệ thống, vui lòng thử lại');
  }
  return data;
}
