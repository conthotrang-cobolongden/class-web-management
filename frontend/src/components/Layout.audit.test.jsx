import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import Layout from './Layout.jsx';

let currentUser;

vi.mock('../auth.jsx', () => ({
  ROLE_LABELS: { admin: 'Quản trị', teacher: 'GV chính', assistant: 'Trợ giảng' },
  useAuth: () => ({ user: currentUser, logout: vi.fn() }),
}));

function renderLayout(user) {
  currentUser = { id: 1, full_name: 'Test', ...user };
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<div>Tổng quan</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('audit route and navigation', () => {
  beforeEach(() => {
    currentUser = undefined;
  });

  it('shows audit navigation in desktop and mobile navigation for admins', async () => {
    renderLayout({ role: 'admin' });

    expect(screen.getByRole('link', { name: 'Nhật ký' })).toHaveAttribute(
      'href', '/logs',
    );

    await userEvent.click(screen.getByRole('button', { name: 'Thêm' }));
    const links = screen.getAllByRole('link', { name: 'Nhật ký' });
    expect(links).toHaveLength(2);
    expect(links[1]).toHaveAttribute('href', '/logs');
  });

  it.each(['teacher', 'assistant'])(
    'hides audit navigation from %s users',
    async (role) => {
      renderLayout({ role });
      expect(screen.queryByRole('link', { name: 'Nhật ký' })).not.toBeInTheDocument();

      await userEvent.click(screen.getByRole('button', { name: 'Thêm' }));
      expect(screen.queryByRole('link', { name: 'Nhật ký' })).not.toBeInTheDocument();
    },
  );

  it('redirects a teacher away from the audit route', async () => {
    currentUser = { id: 2, full_name: 'Teacher', role: 'teacher' };
    render(
      <MemoryRouter initialEntries={['/logs']}>
        <App />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.queryByText('Nhật ký hoạt động')).not.toBeInTheDocument();
      expect(screen.getByRole('heading', { name: 'Tổng quan' })).toBeInTheDocument();
    });
  });

  it('allows an admin to open the audit route', async () => {
    currentUser = { id: 1, full_name: 'Admin', role: 'admin' };
    render(
      <MemoryRouter initialEntries={['/logs']}>
        <App />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Nhật ký hoạt động')).toBeInTheDocument();
  });
});
