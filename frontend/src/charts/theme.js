// Theme bieu do — theo dataviz skill (palette da chay validator: PASS ca 6 check).
// QUY TAC: toi da 3 series/1 chart (3 slot dau da validate all-pairs);
// mau status CHI dung cho trang thai (kem nhan chu, khong bao gio dung lam series);
// text luon mau ink, khong bao gio mau series; 1 truc y duy nhat.

export const SERIES = ['#2a78d6', '#eb6834', '#1baf7a']; // slot 1-3: blue, orange, aqua

export const STATUS = {
  good: '#0ca30c',      // present / da dong
  warning: '#fab219',   // late / dong mot phan
  serious: '#ec835a',
  critical: '#d03b3b',  // absent / chua dong
  neutral: '#898781',   // excused / khac
};

export const INK = {
  primary: '#0b0b0b',
  secondary: '#52514e',
  muted: '#898781',     // truc + nhan
  grid: '#e1e0d9',      // hairline gridline
  baseline: '#c3c2b7',
  surface: '#fcfcfb',
};

// Props dung chung cho Recharts — luoi lui ve sau, truc chu nho mau muted
export const gridProps = { stroke: INK.grid, strokeDasharray: '0', vertical: false };
export const axisProps = {
  stroke: INK.baseline,
  tick: { fill: INK.muted, fontSize: 12 },
  tickLine: false,
  axisLine: { stroke: INK.baseline },
};

export const tooltipStyle = {
  contentStyle: {
    background: '#fff',
    border: '1px solid rgba(11,11,11,0.10)',
    borderRadius: 10,
    fontSize: 13,
    boxShadow: '0 4px 16px rgba(0,0,0,0.08)',
  },
  labelStyle: { color: INK.secondary, fontWeight: 600 },
  cursor: { fill: 'rgba(11,11,11,0.04)' },
};

export function fmtCompactMoney(v) {
  if (v == null) return '';
  const n = Number(v);
  if (Math.abs(n) >= 1e9) return (n / 1e9).toFixed(1).replace(/\.0$/, '') + ' tỷ';
  if (Math.abs(n) >= 1e6) return (n / 1e6).toFixed(1).replace(/\.0$/, '') + ' tr';
  if (Math.abs(n) >= 1e3) return Math.round(n / 1e3) + 'k';
  return String(n);
}
