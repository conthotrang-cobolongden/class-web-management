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
  constructor(status, message, data = null) {
    super(message);
    this.status = status;
    this.data = data;
  }
}

export async function api(path, { method = 'GET', body } = {}) {
  if (import.meta.env.VITE_DEMO_MODE === 'true') {
    if (method !== 'GET') {
      throw new ApiError(400, 'Đây là bản demo, dữ liệu không được lưu.');
    }

    if (path.startsWith('/reports/dashboard')) {
      return {
        active_students: 120,
        classes_by_grade: { 6: 2, 7: 3, 8: 2, 9: 2 },
        new_students_this_month: 12,
        left_students: 3,
        unpaid_fees_this_month: 8,
      };
    }

    if (path.startsWith('/reports/revenue')) {
      return {
        grand_total_due: 45000000,
        grand_total_paid: 38000000,
        items: [],
      };
    }

    if (path.startsWith('/sessions/week')) return [];

    throw new ApiError(400, 'Trang này chưa có dữ liệu demo.');
  }

  const headers = {};
  const isFormData = typeof FormData !== 'undefined' && body instanceof FormData;
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined && !isFormData) headers['Content-Type'] = 'application/json';

  let res;
  try {
    res = await fetch(`/api${path}`, {
      method,
      headers,
      body: body !== undefined ? (isFormData ? body : JSON.stringify(body)) : undefined,
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
    const message = (data && data.error)
      || (data && Array.isArray(data.errors) && `File có ${data.errors.length} lỗi`)
      || 'Lỗi hệ thống, vui lòng thử lại';
    throw new ApiError(res.status, message, data);
  }
  return data;
}

function responseFilename(response, fallback) {
  const disposition = response.headers.get('content-disposition') || '';
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1]);
    } catch {
      return fallback;
    }
  }
  const plain = disposition.match(/filename="?([^";]+)"?/i);
  return plain ? plain[1] : fallback;
}

export async function downloadApiFile(path) {
  const token = getToken();
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;

  let response;
  try {
    response = await fetch(`/api${path}`, { headers });
  } catch {
    throw new ApiError(0, 'Không kết nối được máy chủ, vui lòng kiểm tra mạng và thử lại');
  }

  if (!response.ok) {
    let data = null;
    try {
      data = await response.json();
    } catch {
      // Phản hồi lỗi không có JSON.
    }
    if (response.status === 401 && token) {
      clearSession();
      window.location.assign('/login');
    }
    throw new ApiError(
      response.status,
      (data && data.error) || 'Không tải được file mẫu, vui lòng thử lại',
      data,
    );
  }

  const blob = await response.blob();
  const filename = responseFilename(response, 'mau-danh-sach-hoc-sinh.xlsx');
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = objectUrl;
  link.download = filename;
  link.style.display = 'none';
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(objectUrl);
  return filename;
}
