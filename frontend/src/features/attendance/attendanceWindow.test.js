import { describe, expect, it } from 'vitest';
import { attendanceWindow } from './attendanceWindow.js';

describe('attendanceWindow', () => {
  it('locks controls and explains the server-provided assistant window', () => {
    const state = attendanceWindow({
      can_edit: false,
      edit_lock_reason: 'Đã hết thời gian điểm danh của trợ giảng',
      edit_window_opens_at: '2026-07-28T10:30:00Z',
      edit_window_closes_at: '2026-07-28T13:30:00Z',
    });

    expect(state.disabled).toBe(true);
    expect(state.message).toContain('Đã hết thời gian điểm danh của trợ giảng');
    expect(state.message).toContain('17:30');
    expect(state.message).toContain('20:30');
  });

  it('leaves controls enabled when the backend allows edits', () => {
    expect(attendanceWindow({ can_edit: true })).toEqual({
      disabled: false,
      message: '',
    });
  });
});
