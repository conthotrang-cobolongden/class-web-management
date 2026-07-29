import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from './api.js';

describe('api multipart support', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it('sends FormData without forcing a JSON content type', async () => {
    const form = new FormData();
    form.append('file', new File(['xlsx'], 'students.xlsx'));
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: vi.fn().mockResolvedValue({ imported: 1 }),
    });
    vi.stubGlobal('fetch', fetchMock);

    await api('/classes/7/students/import', { method: 'POST', body: form });

    expect(fetchMock).toHaveBeenCalledWith('/api/classes/7/students/import', expect.objectContaining({
      method: 'POST',
      body: form,
      headers: {},
    }));
  });

  it('keeps structured import errors on ApiError', async () => {
    const data = { errors: [{ row: 2, column: 'Ngày sinh', reason: 'Sai định dạng' }] };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: vi.fn().mockResolvedValue(data),
    }));

    let caught;
    try {
      await api('/classes/7/students/import', { method: 'POST', body: new FormData() });
    } catch (error) {
      caught = error;
    }

    expect(caught).toBeInstanceOf(ApiError);
    expect(caught.message).toBe('File có 1 lỗi');
    expect(caught.data).toEqual(data);
  });
});
