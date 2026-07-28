import { createContext, useContext, useEffect, useState } from 'react';
import { api, clearSession, getSavedUser, getToken, saveSession } from './api.js';

const AuthContext = createContext(null);

export const ROLE_LABELS = {
  admin: 'Quản trị',
  teacher: 'GV chính',
  assistant: 'Trợ giảng',
};

export function AuthProvider({ children }) {
  const [user, setUser] = useState(getSavedUser);

  // Xac thuc lai phien khi mo app: token chet/tai khoan bi khoa -> api() tu clear + ve /login;
  // role/ten bi doi phia server -> dong bo lai (khong tin localStorage vinh vien)
  useEffect(() => {
    if (!getToken()) return;
    api('/auth/me')
      .then((data) => {
        saveSession(getToken(), data.user);
        setUser(data.user);
      })
      .catch(() => {
        // 401 da duoc api() xu ly (clear + redirect); loi mang thi giu phien hien tai
      });
  }, []);

  async function login(email, password) {
    const data = await api('/auth/login', { method: 'POST', body: { email, password } });
    saveSession(data.token, data.user);
    setUser(data.user);
    return data.user;
  }

  function logout() {
    clearSession();
    setUser(null);
  }

  // Cap nhat thong tin ban than trong context + localStorage (vd sau khi admin tu sua ho so)
  function updateUser(u) {
    const token = getToken();
    if (token) saveSession(token, u);
    setUser(u);
  }

  return (
    <AuthContext.Provider value={{ user, login, logout, updateUser }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
