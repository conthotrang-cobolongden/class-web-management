import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth.jsx';
import Layout from './components/Layout.jsx';
import ChangePasswordPage from './pages/ChangePasswordPage.jsx';
import HomePage from './pages/HomePage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import UsersPage from './pages/UsersPage.jsx';
import ClassesPage from './pages/classes/ClassesPage.jsx';
import ClassDetailPage from './pages/classes/ClassDetailPage.jsx';
import StudentsPage from './pages/students/StudentsPage.jsx';
import StudentDetailPage from './pages/students/StudentDetailPage.jsx';
import AttendancePage from './pages/attendance/AttendancePage.jsx';
import GradesPage from './pages/grades/GradesPage.jsx';
import FeesPage from './pages/fees/FeesPage.jsx';
import ReportsPage from './pages/reports/ReportsPage.jsx';

function RequireAuth({ children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

function RequireAdmin({ children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== 'admin') return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={(
          <RequireAuth>
            <Layout />
          </RequireAuth>
        )}
      >
        <Route index element={<HomePage />} />
        <Route
          path="users"
          element={(
            <RequireAdmin>
              <UsersPage />
            </RequireAdmin>
          )}
        />
        <Route path="classes" element={<ClassesPage />} />
        <Route path="classes/:id" element={<ClassDetailPage />} />
        <Route path="students" element={<StudentsPage />} />
        <Route path="students/:id" element={<StudentDetailPage />} />
        <Route path="attendance" element={<AttendancePage />} />
        <Route path="grades" element={<GradesPage />} />
        <Route path="fees" element={<FeesPage />} />
        <Route path="reports" element={<ReportsPage />} />
        <Route path="doi-mat-khau" element={<ChangePasswordPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
