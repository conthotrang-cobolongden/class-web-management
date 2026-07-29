import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from '../../api.js';
import AuditLogsPage from './AuditLogsPage.jsx';

vi.mock('../../api.js', () => ({
  api: vi.fn(),
  ApiError: class ApiError extends Error {
    constructor(status, message) {
      super(message);
      this.status = status;
    }
  },
}));

function pageWithOneAudit(overrides = {}) {
  return {
    items: [{
      id: 9,
      category: 'fees',
      action: 'create',
      description: 'Ghi nhận thanh toán',
      table_name: 'payments',
      record_id: 42,
      created_at: '2026-07-29T01:00:00Z',
      actor: { id: 1, full_name: 'Admin', email: 'admin@test.local' },
      old_values: null,
      new_values: { amount: 500000 },
    }],
    total: 1,
    page: 1,
    page_size: 20,
    ...overrides,
  };
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function renderAuditPage() {
  return render(
    <MemoryRouter>
      <AuditLogsPage />
    </MemoryRouter>,
  );
}

async function loadSecondPage() {
  api.mockResolvedValue(pageWithOneAudit({ total: 21 }));
  renderAuditPage();
  await screen.findAllByText('Ghi nhận thanh toán');
  await userEvent.click(screen.getByRole('button', { name: 'Sau →' }));
  await waitFor(() => {
    expect(api).toHaveBeenLastCalledWith(expect.stringContaining('page=2'));
  });
}

describe('AuditLogsPage data behavior', () => {
  beforeEach(() => {
    api.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('loads audit logs and exposes loading skeletons, results, and pagination', async () => {
    const request = deferred();
    api.mockReturnValueOnce(request.promise);
    renderAuditPage();

    expect(screen.getByRole('status', { name: 'Đang tải nhật ký' })).toBeInTheDocument();
    expect(screen.getAllByTestId('audit-skeleton')).toHaveLength(3);
    expect(api).toHaveBeenCalledWith('/audit-logs?page=1&page_size=20');

    request.resolve(pageWithOneAudit({ total: 21 }));
    expect((await screen.findAllByText('Ghi nhận thanh toán')).length).toBeGreaterThan(0);
    expect(screen.getByText('Trang 1/2')).toBeInTheDocument();

  });

  it('moves between pages and requests the selected page', async () => {
    api.mockResolvedValue(pageWithOneAudit({ total: 21 }));
    renderAuditPage();
    await screen.findAllByText('Ghi nhận thanh toán');

    await userEvent.click(screen.getByRole('button', { name: 'Sau →' }));
    await waitFor(() => {
      expect(api).toHaveBeenLastCalledWith('/audit-logs?page=2&page_size=20');
    });

    await userEvent.click(screen.getByRole('button', { name: '← Trước' }));
    await waitFor(() => {
      expect(api).toHaveBeenLastCalledWith('/audit-logs?page=1&page_size=20');
    });
  });

  it('applies category, action, and inclusive local-date filters and resets the page', async () => {
    await loadSecondPage();

    await userEvent.selectOptions(screen.getByLabelText('Danh mục'), 'students');
    await waitFor(() => {
      const path = api.mock.calls.at(-1)[0];
      expect(path).toContain('page=1');
      expect(path).toContain('category=students');
    });

    await userEvent.selectOptions(screen.getByLabelText('Hành động'), 'update');
    await userEvent.type(screen.getByLabelText('Từ ngày'), '2026-07-01');
    await userEvent.type(screen.getByLabelText('Đến ngày'), '2026-07-29');
    await waitFor(() => {
      const path = api.mock.calls.at(-1)[0];
      expect(path).toContain('action=update');
      expect(path).toContain('from=');
      expect(path).toContain('to=');
      expect(path).toContain('page=1');
    });
  });

  it('debounces actor email for 350 ms and resets pagination before loading', async () => {
    await loadSecondPage();
    vi.useFakeTimers();
    const callsBeforeTyping = api.mock.calls.length;

    fireEvent.change(screen.getByLabelText('Email người thao tác'), {
      target: { value: 'admin@' },
    });
    await act(() => vi.advanceTimersByTimeAsync(349));
    expect(api).toHaveBeenCalledTimes(callsBeforeTyping);

    await act(() => vi.advanceTimersByTimeAsync(1));
    expect(api).toHaveBeenLastCalledWith(expect.stringMatching(
      /page=1.*actor_email=admin%40/,
    ));
  });

  it('debounces keyword for 350 ms and resets pagination before loading', async () => {
    await loadSecondPage();
    vi.useFakeTimers();
    const callsBeforeTyping = api.mock.calls.length;

    fireEvent.change(screen.getByLabelText('Từ khóa'), {
      target: { value: 'payment' },
    });
    await act(() => vi.advanceTimersByTimeAsync(349));
    expect(api).toHaveBeenCalledTimes(callsBeforeTyping);

    await act(() => vi.advanceTimersByTimeAsync(1));
    expect(api).toHaveBeenLastCalledWith(expect.stringMatching(/page=1.*q=payment/));
  });

  it('shows a retryable error and retries the current request', async () => {
    api
      .mockRejectedValueOnce(new ApiError(500, 'Máy chủ bận'))
      .mockResolvedValueOnce(pageWithOneAudit());
    renderAuditPage();

    expect(await screen.findByText('Máy chủ bận')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }));

    expect((await screen.findAllByText('Ghi nhận thanh toán')).length).toBeGreaterThan(0);
    expect(api).toHaveBeenCalledTimes(2);
  });

  it('shows the dedicated permission state for HTTP 403 without retry', async () => {
    api.mockRejectedValueOnce(new ApiError(403, 'Cấm'));
    renderAuditPage();

    expect(await screen.findByText('Bạn không có quyền xem nhật ký')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Thử lại' })).not.toBeInTheDocument();
  });

  it('resets active filters from the empty state', async () => {
    api
      .mockResolvedValueOnce(pageWithOneAudit())
      .mockResolvedValue({ items: [], total: 0, page: 1, page_size: 20 });
    renderAuditPage();

    await screen.findAllByText('Ghi nhận thanh toán');
    await userEvent.selectOptions(screen.getByLabelText('Danh mục'), 'students');
    expect(await screen.findByText('Không có nhật ký phù hợp')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Xóa bộ lọc' }));
    expect(screen.getByLabelText('Danh mục')).toHaveValue('');
    await waitFor(() => {
      expect(api).toHaveBeenLastCalledWith('/audit-logs?page=1&page_size=20');
    });
  });

  it('renders rows and opens the read-only desktop details drawer', async () => {
    api.mockResolvedValueOnce(pageWithOneAudit());
    renderAuditPage();

    await screen.findAllByText('Ghi nhận thanh toán');
    const desktopTrigger = screen.getAllByRole('button', {
      name: /Ghi nhận thanh toán/i,
    })[0];
    await userEvent.click(desktopTrigger);

    const dialog = screen.getByRole('dialog', { name: 'Chi tiết nhật ký' });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(within(dialog).getByText('admin@test.local')).toBeInTheDocument();
    expect(within(dialog).getByText((_, element) => element.tagName === 'PRE' && element.textContent.includes('"amount": 500000'))).toBeInTheDocument();
    expect(screen.getAllByText('Không có dữ liệu').length).toBeGreaterThan(0);
    expect(screen.queryByRole('button', { name: /sửa|xóa|xuất/i })).not.toBeInTheDocument();

    const closeButton = screen.getByRole('button', { name: 'Đóng' });
    expect(closeButton).toHaveFocus();
    await userEvent.click(closeButton);
    expect(screen.queryByRole('dialog', { name: 'Chi tiết nhật ký' })).not.toBeInTheDocument();
    await waitFor(() => expect(desktopTrigger).toHaveFocus());
  });

  it('dismisses the desktop drawer with Escape', async () => {
    api.mockResolvedValueOnce(pageWithOneAudit());
    renderAuditPage();

    await screen.findAllByText('Ghi nhận thanh toán');
    await userEvent.click(screen.getAllByRole('button', {
      name: /Ghi nhận thanh toán/i,
    })[0]);
    expect(screen.getByRole('dialog', { name: 'Chi tiết nhật ký' })).toBeInTheDocument();

    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog', { name: 'Chi tiết nhật ký' })).not.toBeInTheDocument();
  });
  it('expands a mobile audit card and reuses safe snapshot details', async () => {
    api.mockResolvedValueOnce(pageWithOneAudit({
      items: [{
        ...pageWithOneAudit().items[0],
        actor: null,
        old_values: '{broken',
        new_values: null,
      }],
    }));
    renderAuditPage();

    const toggles = await screen.findAllByRole('button', {
      name: /Ghi nhận thanh toán/i,
    });
    const mobileToggle = toggles[1];
    expect(mobileToggle).toHaveAttribute('aria-expanded', 'false');
    await userEvent.click(mobileToggle);

    expect(mobileToggle).toHaveAttribute('aria-expanded', 'true');
    const mobileCard = mobileToggle.closest('article');
    expect(within(mobileCard).getAllByText('Không xác định').length).toBeGreaterThan(0);
    expect(within(mobileCard).getByText(/\{broken/)).toBeInTheDocument();
    expect(within(mobileCard).getByText('Không có dữ liệu')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /sửa|xóa|xuất/i })).not.toBeInTheDocument();
  });

  it('closes an open drawer when pagination changes', async () => {
    api.mockResolvedValue(pageWithOneAudit({ total: 21 }));
    renderAuditPage();

    await screen.findAllByText('Ghi nhận thanh toán');
    await userEvent.click(screen.getAllByRole('button', {
      name: /Ghi nhận thanh toán/i,
    })[0]);
    expect(screen.getByRole('dialog', { name: 'Chi tiết nhật ký' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Sau →' }));
    expect(screen.queryByRole('dialog', { name: 'Chi tiết nhật ký' })).not.toBeInTheDocument();
  });
  it('ignores a stale response that arrives after a newer filtered response', async () => {
    const initial = deferred();
    const filtered = deferred();
    api.mockReturnValueOnce(initial.promise).mockReturnValueOnce(filtered.promise);
    renderAuditPage();

    await userEvent.selectOptions(screen.getByLabelText('Danh mục'), 'students');
    expect(api).toHaveBeenLastCalledWith(expect.stringContaining('category=students'));

    filtered.resolve(pageWithOneAudit({
      items: [{ ...pageWithOneAudit().items[0], id: 10, description: 'Kết quả mới' }],
    }));
    expect((await screen.findAllByText('Kết quả mới')).length).toBeGreaterThan(0);

    initial.resolve(pageWithOneAudit({
      items: [{ ...pageWithOneAudit().items[0], description: 'Kết quả cũ' }],
    }));
    await act(async () => {});

    expect(screen.getAllByText('Kết quả mới').length).toBeGreaterThan(0);
    expect(screen.queryByText('Kết quả cũ')).not.toBeInTheDocument();
  });
});
