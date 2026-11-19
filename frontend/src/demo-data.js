// Dữ liệu hư cấu, chỉ dùng cho bản xem thử tĩnh trên Vercel.
const now = new Date();
const year = now.getFullYear();
const users = [
  { id: 1, full_name: 'Quản trị demo', email: 'demo@example.com', role: 'admin', is_active: true },
  { id: 2, full_name: 'Nguyễn Minh Anh', email: 'minhanh@example.com', role: 'teacher', is_active: true },
  { id: 3, full_name: 'Trần Hoàng Nam', email: 'hoangnam@example.com', role: 'teacher', is_active: true },
  { id: 4, full_name: 'Lê Phương Linh', email: 'phuonglinh@example.com', role: 'assistant', is_active: true },
];

const names = [
  'Nguyễn Gia Hân', 'Trần Minh Khang', 'Lê Bảo Ngọc', 'Phạm Đức Anh',
  'Hoàng Khánh An', 'Vũ Nhật Minh', 'Đặng Thảo Vy', 'Bùi Quốc Bảo',
  'Đỗ Thanh Trúc', 'Phan Tuấn Kiệt', 'Ngô Hà My', 'Dương Anh Tuấn',
  'Đinh Mai Anh', 'Trịnh Gia Bảo', 'Lý Ngọc Anh', 'Võ Minh Quân',
  'Nguyễn Thiên Kim', 'Trần Hải Đăng', 'Lê Phương Anh', 'Phạm Thành Đạt',
  'Hoàng Yến Nhi', 'Vũ Hoàng Long', 'Đặng Minh Châu', 'Bùi Gia Huy',
  'Đỗ Hương Giang', 'Phan Nhật Nam', 'Ngô Bảo Trân', 'Dương Khánh Duy',
  'Đinh Tuệ Nhi', 'Trịnh Quang Huy', 'Lý Ngọc Hà', 'Võ Đức Minh',
];

const classes = Array.from({ length: 8 }, (_, index) => {
  const id = index + 1;
  const grade = 6 + Math.floor(index / 2);
  const teacher = users[1 + (index % 2)];
  return {
    id, name: `Toán ${grade}${index % 2 ? 'B' : 'A'}`, grade,
    school_year: `${year}-${year + 1}`,
    start_date: `${year}-08-01`, end_date: `${year + 1}-05-31`,
    default_monthly_fee: 600000 + (grade - 6) * 50000,
    student_count: 4, status: 'active', main_teacher: teacher,
    staff: [
      { user_id: teacher.id, full_name: teacher.full_name, staff_role: 'main' },
      { user_id: 4, full_name: users[3].full_name, staff_role: 'assistant' },
    ],
    schedules: [
      { id: id * 10 + 1, day_of_week: 2 + (index % 3), start_time: '17:30', end_time: '19:00' },
      { id: id * 10 + 2, day_of_week: 6, start_time: '08:00', end_time: '09:30' },
    ],
    note: 'Lớp học minh họa cho bản xem thử.',
  };
});

const students = names.map((full_name, index) => {
  const cls = classes[Math.floor(index / 4)];
  return {
    id: index + 1, student_code: `HS${String(index + 1).padStart(6, '0')}`,
    full_name, dob: `${year - cls.grade - 6}-${String(index % 12 + 1).padStart(2, '0')}-15`,
    gender: index % 2 ? 'male' : 'female', school_name: 'THCS Ánh Dương',
    parent_name: 'Phụ huynh demo', parent_phone: '—', enrollment_date: `${year}-08-01`,
    status: 'active', current_class: { id: cls.id, name: cls.name },
    enrollments: [{ class_id: cls.id, class_name: cls.name, joined_at: `${year}-08-01`, left_at: null }],
  };
});

function page(items, params) {
  const number = Math.max(1, Number(params.get('page')) || 1);
  const size = Math.max(1, Number(params.get('page_size')) || 20);
  return { items: items.slice((number - 1) * size, number * size), total: items.length, page: number, page_size: size };
}

function dateString(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function sessionsForRange(from, to) {
  const start = new Date(`${from}T12:00:00`);
  const end = new Date(`${to}T12:00:00`);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) return [];
  const sessions = [];
  for (let day = new Date(start), count = 0; day <= end && count < 62; day.setDate(day.getDate() + 1), count++) {
    const date = dateString(day);
    for (const cls of classes) {
      const weekday = day.getDay() || 7;
      const schedule = cls.schedules.find((item) => item.day_of_week === weekday);
      if (!schedule) continue;
      sessions.push({
        id: Number(date.replaceAll('-', '')) * 10 + cls.id,
        class_id: cls.id, class_name: cls.name, session_date: date,
        start_time: schedule.start_time, end_time: schedule.end_time,
        status: 'scheduled', is_makeup: false, attendance_count: 4,
      });
    }
  }
  return sessions;
}

function sessionById(id) {
  const raw = String(id);
  const clsId = Number(raw.slice(-1));
  const date = `${raw.slice(0, 4)}-${raw.slice(4, 6)}-${raw.slice(6, 8)}`;
  return sessionsForRange(date, date).find((item) => item.class_id === clsId);
}

function classStudents(id) {
  return students.filter((student) => student.current_class.id === Number(id));
}

function feesForClass(cls) {
  return classStudents(cls.id).map((student, index) => {
    const status = ['paid', 'paid', 'partial', 'unpaid'][index];
    const amount_due = cls.default_monthly_fee;
    return {
      id: student.id, student_id: student.id, student_name: student.full_name,
      class_id: cls.id, class_name: cls.name, amount_due, base_amount: amount_due,
      paid_total: status === 'paid' ? amount_due : status === 'partial' ? amount_due / 2 : 0,
      status,
    };
  });
}

export function demoApi(path) {
  const url = new URL(path, 'https://demo.example');
  const params = url.searchParams;
  const route = url.pathname;
  let match;

  if (route === '/reports/dashboard') {
    return {
      active_students: students.length, classes_by_grade: { 6: 2, 7: 2, 8: 2, 9: 2 },
      new_students_this_month: 4, left_students: 0, unpaid_fees_this_month: 16,
    };
  }
  if (route === '/classes') {
    return page(classes.filter((cls) => (!params.get('grade') || String(cls.grade) === params.get('grade'))
      && (!params.get('status') || cls.status === params.get('status'))), params);
  }
  if (route === '/users') {
    return page(users.filter((user) => (!params.get('role') || user.role === params.get('role'))
      && (params.get('active') !== 'true' || user.is_active)), params);
  }
  if (route === '/students') {
    const query = (params.get('q') || '').toLocaleLowerCase('vi');
    return page(students.filter((student) => (!params.get('class_id') || String(student.current_class.id) === params.get('class_id'))
      && (!params.get('status') || student.status === params.get('status'))
      && (!query || `${student.full_name} ${student.student_code}`.toLocaleLowerCase('vi').includes(query))), params);
  }
  if ((match = route.match(/^\/students\/(\d+)\/transfer-options$/))) {
    const student = students.find((item) => item.id === Number(match[1]));
    return classes.filter((cls) => cls.id !== student?.current_class.id);
  }
  if ((match = route.match(/^\/students\/(\d+)$/))) {
    return { student: students.find((item) => item.id === Number(match[1])) || null };
  }
  if ((match = route.match(/^\/classes\/(\d+)\/students$/))) {
    return { items: classStudents(match[1]).map((student) => ({ ...student, student_id: student.id, joined_at: student.enrollment_date })) };
  }
  if ((match = route.match(/^\/classes\/(\d+)\/grades$/))) {
    const columns = [
      { id: 1, name: 'Kiểm tra 15 phút', weight: 1, max_score: 10 },
      { id: 2, name: 'Giữa kỳ', weight: 2, max_score: 10 },
      { id: 3, name: 'Cuối kỳ', weight: 3, max_score: 10 },
    ];
    return { columns, items: classStudents(match[1]).map((student, index) => ({
      student_id: student.id, full_name: student.full_name,
      scores: { 1: 7 + index * 0.5, 2: 8 + index * 0.5, 3: 7.5 + index * 0.5 },
    })) };
  }
  if ((match = route.match(/^\/classes\/(\d+)$/))) {
    return { class: classes.find((item) => item.id === Number(match[1])) || null };
  }
  if (route === '/sessions/week' || route === '/sessions') {
    const from = params.get('from') || dateString(new Date(year, now.getMonth(), 1));
    const to = params.get('to') || dateString(new Date(year, now.getMonth() + 1, 0));
    const items = sessionsForRange(from, to).filter((item) => !params.get('class_id') || String(item.class_id) === params.get('class_id'));
    return route === '/sessions/week' ? items : page(items, params);
  }
  if ((match = route.match(/^\/sessions\/(\d+)\/attendance$/))) {
    const session = sessionById(match[1]);
    return { session, can_edit: true, items: classStudents(session?.class_id).map((student, index) => ({
      student_id: student.id, full_name: student.full_name,
      status: index === 3 ? 'late' : 'present', note: '',
    })) };
  }
  if (route === '/fees') {
    const items = classes.flatMap(feesForClass).filter((fee) => (!params.get('class_id') || String(fee.class_id) === params.get('class_id'))
      && (!params.get('status') || fee.status === params.get('status')));
    return page(items, params);
  }
  if ((match = route.match(/^\/fees\/(\d+)\/payments$/))) {
    const fee = classes.flatMap(feesForClass).find((item) => item.id === Number(match[1]));
    return { items: fee?.paid_total ? [{ id: fee.id, amount: fee.paid_total, method: 'cash', paid_at: new Date().toISOString(), note: 'Thanh toán mẫu' }] : [] };
  }
  if (route === '/reports/revenue') {
    const selected = classes.filter((cls) => !params.get('class_id') || String(cls.id) === params.get('class_id'));
    const factor = 0.9 + ((Number(params.get('month')) || 1) % 3) * 0.05;
    const items = selected.map((cls) => {
      const fees = feesForClass(cls);
      return { class_id: cls.id, class_name: cls.name,
        total_due: Math.round(fees.reduce((sum, fee) => sum + fee.amount_due, 0) * factor),
        total_paid: Math.round(fees.reduce((sum, fee) => sum + fee.paid_total, 0) * factor),
        paid_count: 2, partial_count: 1, unpaid_count: 1 };
    });
    return { items, grand_total_due: items.reduce((sum, item) => sum + item.total_due, 0), grand_total_paid: items.reduce((sum, item) => sum + item.total_paid, 0) };
  }
  if (route === '/reports/attendance') {
    const items = classStudents(params.get('class_id')).map((student, index) => ({
      student_id: student.id, full_name: student.full_name, present: 8 - index, absent: index,
      late: 0, excused: 0, total_sessions: 8, attendance_rate: (8 - index) / 8,
    }));
    return { items, summary: { total_sessions: 8, avg_rate: 0.8125 }, frequent_absentees: items.filter((item) => item.absent >= 3) };
  }
  if (route === '/audit-logs') {
    const items = [
      { id: 1, category: 'attendance', action: 'update', table_name: 'attendance', description: 'Cập nhật điểm danh lớp Toán 6A', actor: users[1] },
      { id: 2, category: 'fees', action: 'create', table_name: 'payments', description: 'Ghi nhận học phí tháng này', actor: users[0] },
      { id: 3, category: 'students', action: 'create', table_name: 'students', description: 'Thêm học sinh mới vào lớp Toán 7A', actor: users[2] },
    ].map((item, index) => ({ ...item, created_at: new Date(Date.now() - index * 86400000).toISOString(), record_id: item.id }));
    return page(items.filter((item) => (!params.get('category') || item.category === params.get('category'))
      && (!params.get('action') || item.action === params.get('action'))), params);
  }
  throw new Error('Chức năng này chưa có dữ liệu trong bản demo.');
}
