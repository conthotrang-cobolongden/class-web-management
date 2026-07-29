import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, downloadApiFile } from '../../api.js';
import StudentImportPanel from './StudentImportPanel.jsx';

vi.mock('../../api.js', () => ({
  api: vi.fn(),
  downloadApiFile: vi.fn(),
}));

const classes = [
  { id: 7, name: 'Lớp 8A', school_year: '2026-2027', status: 'active' },
  { id: 9, name: 'Lớp đã đóng', school_year: '2025-2026', status: 'closed' },
];

describe('StudentImportPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('requires an active class before template download or upload', async () => {
    render(<StudentImportPanel classes={classes} />);

    expect(screen.getByRole('button', { name: /tải file mẫu/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /nhập danh sách/i })).toBeDisabled();
    expect(screen.queryByRole('option', { name: /lớp đã đóng/i })).not.toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText(/lớp nhận học sinh/i), '7');
    expect(screen.getByRole('button', { name: /tải file mẫu/i })).toBeEnabled();
  });

  it('downloads the template for the selected class', async () => {
    downloadApiFile.mockResolvedValue('mau-danh-sach-hoc-sinh-lop-7.xlsx');
    render(<StudentImportPanel classes={classes} />);

    await userEvent.selectOptions(screen.getByLabelText(/lớp nhận học sinh/i), '7');
    await userEvent.click(screen.getByRole('button', { name: /tải file mẫu/i }));

    expect(downloadApiFile).toHaveBeenCalledWith('/classes/7/students/import-template');
  });

  it('uploads multipart data and reports imported student codes', async () => {
    api.mockResolvedValue({ imported: 2, student_codes: ['HS000041', 'HS000042'], errors: [] });
    const onImported = vi.fn();
    render(<StudentImportPanel classes={classes} onImported={onImported} />);

    await userEvent.selectOptions(screen.getByLabelText(/lớp nhận học sinh/i), '7');
    const file = new File(['xlsx'], 'hoc-sinh.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    await userEvent.upload(screen.getByLabelText(/file excel/i), file);
    fireEvent.submit(screen.getByRole('button', { name: /nhập danh sách/i }).closest('form'));

    expect(api).toHaveBeenCalledWith('/classes/7/students/import', {
      method: 'POST',
      body: expect.any(FormData),
    });
    const formData = api.mock.calls[0][1].body;
    expect(formData.get('file')).toBe(file);
    expect(await screen.findByText(/đã nhập 2 học sinh/i)).toBeInTheDocument();
    expect(screen.getByText(/HS000041, HS000042/)).toBeInTheDocument();
    expect(onImported).toHaveBeenCalled();
  });

  it('renders every structured row error returned by the server', async () => {
    api.mockRejectedValue(Object.assign(new Error('File có lỗi'), {
      data: {
        errors: [
          { row: 3, column: 'Ngày sinh', reason: 'Ngày phải đúng định dạng dd/MM/yyyy' },
          { row: 5, column: 'Họ và tên', reason: 'Học sinh đã tồn tại' },
        ],
      },
    }));
    render(<StudentImportPanel classes={classes} />);

    await userEvent.selectOptions(screen.getByLabelText(/lớp nhận học sinh/i), '7');
    await userEvent.upload(
      screen.getByLabelText(/file excel/i),
      new File(['xlsx'], 'hoc-sinh.xlsx'),
    );
    fireEvent.submit(screen.getByRole('button', { name: /nhập danh sách/i }).closest('form'));

    expect(await screen.findByText('Ngày phải đúng định dạng dd/MM/yyyy')).toBeInTheDocument();
    expect(screen.getByText('Học sinh đã tồn tại')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('5')).toBeInTheDocument();
  });
});
