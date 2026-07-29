# Local Workflows, Permissions, Excel Import, and Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved assistant permissions, attendance time window, privacy-aware fee summary, weekly overview timetable, student codes, atomic Excel import, and categorized Admin audit log.

**Architecture:** Extend the existing Spring Boot services and React pages without replacing current routes or data relationships. Authorization and time-window decisions stay in backend policy services; frontend helpers mirror server state for clear UX. Flyway migrations remain vendor-specific, Excel handling uses Apache POI 5.5.1, and audit writes join each domain transaction.

**Tech Stack:** Java 21, Spring Boot 3.5.3, Spring Data JPA, Flyway, SQL Server/PostgreSQL, Apache POI 5.5.1, React 18, Vite 5, Vitest, React Testing Library.

---

## File Map

### Backend additions

- `java-backend/src/main/resources/db/migration/{sqlserver,postgresql}/V3__student_codes_and_audit_log.sql`: portable student-code backfill and audit schema expansion.
- `java-backend/src/main/java/com/classmanagement/common/AttendanceEditPolicy.java`: role-aware, clock-testable attendance window.
- `java-backend/src/main/java/com/classmanagement/audit/*`: Admin audit query service/controller/DTOs.
- `java-backend/src/main/java/com/classmanagement/students/StudentImportService.java`: template generation, workbook parsing, all-or-nothing validation/import.
- `java-backend/src/main/java/com/classmanagement/students/dto/StudentImport*.java`: structured import results and validation errors.
- `java-backend/src/main/java/com/classmanagement/sessions/dto/WeeklySessionDto.java`: timetable response.

### Frontend additions

- `frontend/src/pages/home/WeeklyTimetable.jsx` and `.css`: responsive desktop grid/mobile agenda.
- `frontend/src/pages/students/StudentImportPanel.jsx`: class selection, template download, upload, error report.
- `frontend/src/pages/audit/AuditLogsPage.jsx` and `.css`: Admin filters, paginated table/cards, detail drawer.
- `frontend/src/features/attendance/attendanceWindow.js`, `features/fees/feeSummary.js`, and `features/timetable/weeklyTimetable.js`: pure tested presentation helpers.

### Existing files changed together

- Backend: domain entities/repositories, `ClassAccessService`, `AttendanceService`, `GradeService`, `StudentService`, `FeeService`, `SessionService`, `ClassService`, `UserService`, `AuthService`, and their controllers/DTOs.
- Frontend: `App.jsx`, `Layout.jsx`, `HomePage.jsx`, `home.css`, attendance/grades/fees/student pages, `package.json`, and `package-lock.json`.

---

### Task 1: Establish Automated Test Harnesses

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Create: `frontend/src/test/setup.js`
- Create: `java-backend/src/test/java/com/classmanagement/TestUsers.java`

- [ ] **Step 1: Add a deliberately failing frontend smoke test**

```js
// frontend/src/features/smoke.test.js
import { describe, expect, it } from 'vitest';

describe('frontend test harness', () => {
  it('runs Vitest', () => expect(true).toBe(false));
});
```

- [ ] **Step 2: Install and configure Vitest**

Run:

```powershell
cd frontend
npm install --save-dev vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event
```

Add scripts `"test": "vitest run"` and `"test:watch": "vitest"`, configure `test.environment = 'jsdom'` and `setupFiles = './src/test/setup.js'` in `vite.config.js`, and import `@testing-library/jest-dom/vitest` from setup.

- [ ] **Step 3: Run the smoke test and verify RED**

Run: `npm test -- src/features/smoke.test.js`

Expected: one assertion failure, proving discovery and execution work.

- [ ] **Step 4: Change the assertion to `expect(true).toBe(true)` and verify GREEN**

Run: `npm test -- src/features/smoke.test.js`

Expected: one passing test.

- [ ] **Step 5: Add a backend test fixture factory and verify Maven discovers tests**

`TestUsers` exposes `admin(id)`, `teacher(id)`, and `assistant(id)` methods that construct active `User` instances with the corresponding role. Add a one-assertion JUnit smoke test, run `mvn test`, then remove both smoke tests after the harness is proven.

- [ ] **Step 6: Commit**

```powershell
git add frontend/package.json frontend/package-lock.json frontend/vite.config.js frontend/src/test java-backend/src/test
git commit -m "test: establish frontend and backend harnesses"
```

### Task 2: Add Student Codes and Expand Audit Storage

**Files:**
- Create: `java-backend/src/main/resources/db/migration/sqlserver/V3__student_codes_and_audit_log.sql`
- Create: `java-backend/src/main/resources/db/migration/postgresql/V3__student_codes_and_audit_log.sql`
- Modify: `java-backend/src/main/java/com/classmanagement/domain/Student.java`
- Modify: `java-backend/src/main/java/com/classmanagement/domain/StudentRepository.java`
- Modify: `java-backend/src/main/java/com/classmanagement/students/StudentService.java`
- Modify: `java-backend/src/main/java/com/classmanagement/students/dto/StudentDto.java`
- Modify: `java-backend/src/main/java/com/classmanagement/students/dto/StudentDetailDto.java`
- Modify: `java-backend/src/main/java/com/classmanagement/classes/dto/ClassStudentDto.java`
- Modify: `java-backend/src/main/java/com/classmanagement/domain/AuditLog.java`
- Modify: `java-backend/src/main/java/com/classmanagement/domain/AuditLogRepository.java`
- Create: `java-backend/src/test/java/com/classmanagement/students/StudentCodeTest.java`

- [ ] **Step 1: Write failing student-code format tests**

```java
@Test
void formatsInternalIdAsStableStudentCode() {
    assertEquals("HS000001", StudentCodes.fromId(1L));
    assertEquals("HS123456", StudentCodes.fromId(123456L));
}
```

Run: `mvn -Dtest=StudentCodeTest test`

Expected: compilation failure because `StudentCodes` does not exist.

- [ ] **Step 2: Implement `StudentCodes.fromId` and entity/repository fields**

Create a package-private utility that rejects null/non-positive IDs and returns `"HS%06d".formatted(id)`. Add `studentCode` to `Student` with getter/setter, expose it through student/class roster DTOs, include it in `StudentService` search, and add `findByStudentCodeIgnoreCase` plus duplicate-search query support to `StudentRepository`.

- [ ] **Step 3: Add equivalent V3 migrations**

SQL Server adds nullable `student_code`, backfills `HS` plus at least six digits without truncating IDs longer than six digits, makes it non-null, and creates a unique index. PostgreSQL performs the equivalent with a length guard around `LPAD`, also preserving IDs longer than six digits.

Both migrations add `category`, `description`, make `record_id`/`user_id` nullable, replace the action check with `create`, `update`, `delete`, `login_success`, `login_failure`, and `import`, and index `category, created_at`.

- [ ] **Step 4: Update `AuditLog` mapping and verify tests/build**

Add category/description fields, make record/user mappings nullable, and add getters. Run `mvn test` and `mvn -DskipTests compile`.

- [ ] **Step 5: Start against local SQL Server once to verify Flyway V3 and backfill**

Expected: application starts, `/api/health` returns `200`, and all existing students have unique `HS` codes.

- [ ] **Step 6: Commit**

```powershell
git add java-backend/src/main/resources/db/migration java-backend/src/main/java/com/classmanagement/domain java-backend/src/main/java/com/classmanagement/students java-backend/src/test
git commit -m "feat: add stable student codes and audit schema"
```

### Task 3: Enforce Role-Aware Attendance Windows

**Files:**
- Create: `java-backend/src/main/java/com/classmanagement/common/AttendanceEditPolicy.java`
- Modify: `java-backend/src/main/java/com/classmanagement/attendance/AttendanceService.java`
- Modify: `java-backend/src/main/java/com/classmanagement/attendance/dto/AttendanceRosterResponse.java`
- Create: `java-backend/src/test/java/com/classmanagement/common/AttendanceEditPolicyTest.java`
- Create: `frontend/src/features/attendance/attendanceWindow.js`
- Create: `frontend/src/features/attendance/attendanceWindow.test.js`
- Modify: `frontend/src/pages/attendance/AttendancePage.jsx`

- [ ] **Step 1: Write failing boundary tests using a fixed `Clock`**

Cover assistant at start−30m, end+30m, and one second beyond; teacher at start+24h and beyond; Admin after both windows. The desired API is:

```java
AttendanceEditDecision decision = policy.evaluate(user, session);
assertTrue(decision.allowed());
assertEquals(windowOpen, decision.opensAt());
assertEquals(windowClose, decision.closesAt());
```

Run: `mvn -Dtest=AttendanceEditPolicyTest test` and verify RED.

- [ ] **Step 2: Implement the minimal policy**

Inject `Clock`, use `TimeUtil.sessionStartUtc` and a new end-time UTC conversion, apply inclusive boundaries, and return a reason plus open/close instants. Cancelled sessions return a denied decision before role checks.

- [ ] **Step 3: Apply the policy in `AttendanceService.save` and expose it from roster**

Replace the embedded 24-hour calculation. A denied non-cancelled write throws `403` with the formatted allowed interval. Roster adds `can_edit`, `edit_window_opens_at`, `edit_window_closes_at`, and `edit_lock_reason`.

- [ ] **Step 4: Write frontend RED tests for server-driven locked state**

Test that `attendanceWindow(roster)` returns disabled controls and a Vietnamese explanation when `can_edit` is false.

- [ ] **Step 5: Update `AttendancePage` to trust server window state and verify GREEN**

Remove the frontend-only 24-hour calculation, disable all status/note/save controls when locked, and keep the existing unsaved-change protection.

- [ ] **Step 6: Run targeted and full tests, then commit**

```powershell
mvn -Dtest=AttendanceEditPolicyTest test
npm test -- src/features/attendance/attendanceWindow.test.js
git add java-backend/src frontend/src
git commit -m "feat: enforce assistant attendance time window"
```

### Task 4: Grant Approved Assistant Grade and Transfer Rights

**Files:**
- Modify: `java-backend/src/main/java/com/classmanagement/grades/GradeService.java`
- Modify: `java-backend/src/main/java/com/classmanagement/students/StudentService.java`
- Modify: `java-backend/src/main/java/com/classmanagement/common/ClassAccessService.java`
- Create: `java-backend/src/test/java/com/classmanagement/grades/GradeColumnPermissionTest.java`
- Create: `java-backend/src/test/java/com/classmanagement/students/StudentTransferPermissionTest.java`
- Modify: `frontend/src/pages/grades/GradesPage.jsx`
- Modify: `frontend/src/pages/students/StudentDetailPage.jsx`

- [ ] **Step 1: Write RED permission tests**

Verify an assigned assistant can create/update/delete columns and transfer from the assigned source class; an unrelated assistant receives `403`; destination membership is not required; inactive destinations still fail.

- [ ] **Step 2: Add a named `assertClassStaffOrAdmin` policy and use it**

The helper accepts Admin or class staff with `main`/`assistant`. Replace manager checks only in grade-column CRUD and student transfer. Do not broaden class configuration, fee adjustment, or unrelated student-edit permissions.

- [ ] **Step 3: Update frontend capability checks**

Grades shows “Quản lý cột điểm” to any assigned class member. Student detail shows transfer to Admin, the main teacher, or assigned assistant for the current class. Target options remain all active classes except the source.

- [ ] **Step 4: Run tests and commit**

```powershell
mvn -Dtest=GradeColumnPermissionTest,StudentTransferPermissionTest test
npm test
git add java-backend/src frontend/src/pages
git commit -m "feat: grant assistant grade-column and transfer rights"
```

### Task 5: Redact Assistant Fee Aggregates and Audit Payments

**Files:**
- Create: `frontend/src/features/fees/feeSummary.js`
- Create: `frontend/src/features/fees/feeSummary.test.js`
- Modify: `frontend/src/pages/fees/FeesPage.jsx`
- Modify: `java-backend/src/main/java/com/classmanagement/fees/FeeService.java`
- Create: `java-backend/src/test/java/com/classmanagement/fees/FeeAuditTest.java`

- [ ] **Step 1: Write RED fee-summary tests**

```js
expect(summaryForRole('assistant', rows)).toEqual({ owingCount: 2 });
expect(summaryForRole('teacher', rows)).toMatchObject({ due: 3000000, paid: 1000000 });
```

- [ ] **Step 2: Implement summary helper and assistant presentation**

For assistants render a single “Học sinh chưa đóng” card. Do not render total due, total paid, remaining, or percentage. Preserve per-student amount/history and payment buttons; preserve the existing manager-only adjustment control.

- [ ] **Step 3: Write RED payment-audit test and implement audit call**

A successful payment logs category `fees`, action `create`, payment ID, monthly fee/student/class context, amount, method, and actor in the same transaction. Verify rejected payments do not create a success log.

- [ ] **Step 4: Verify and commit**

```powershell
npm test -- src/features/fees/feeSummary.test.js
mvn -Dtest=FeeAuditTest test
git add frontend/src java-backend/src
git commit -m "feat: protect assistant fee summaries"
```

### Task 6: Add the Permission-Filtered Weekly Session API

**Files:**
- Modify: `java-backend/src/main/java/com/classmanagement/domain/ClassSessionRepository.java`
- Modify: `java-backend/src/main/java/com/classmanagement/sessions/SessionService.java`
- Modify: `java-backend/src/main/java/com/classmanagement/sessions/SessionController.java`
- Create: `java-backend/src/main/java/com/classmanagement/sessions/dto/WeeklySessionDto.java`
- Create: `java-backend/src/test/java/com/classmanagement/sessions/WeeklySessionServiceTest.java`

- [ ] **Step 1: Write RED tests for range and visibility**

Test Monday–Sunday validation, Admin all-class results, staff assigned-class results, exclusion of unrelated classes, chronological ordering, and inclusion of scheduled/cancelled/makeup sessions.

- [ ] **Step 2: Add `/api/sessions/week?from=YYYY-MM-DD&to=YYYY-MM-DD`**

Require exactly seven inclusive dates. Query all sessions for Admin or `visibleClassIds` for staff, batch-load class names and attendance counts, and return a simple list ordered by date/start/class/id.

- [ ] **Step 3: Run tests and commit**

```powershell
mvn -Dtest=WeeklySessionServiceTest test
git add java-backend/src
git commit -m "feat: expose weekly timetable sessions"
```

### Task 7: Build the Responsive Weekly Timetable

**Files:**
- Create: `frontend/src/features/timetable/weeklyTimetable.js`
- Create: `frontend/src/features/timetable/weeklyTimetable.test.js`
- Create: `frontend/src/pages/home/WeeklyTimetable.jsx`
- Create: `frontend/src/pages/home/WeeklyTimetable.css`
- Modify: `frontend/src/pages/HomePage.jsx`
- Modify: `frontend/src/pages/home.css`

- [ ] **Step 1: Write RED date/grouping tests**

Test Monday calculation, previous/next offsets, seven-day labels, grouping by day and start time, concurrent-session stacking, and cancelled/makeup display metadata.

- [ ] **Step 2: Implement pure grouping utilities and verify GREEN**

Use local calendar dates without UTC day drift. Return stable groups keyed by `session_date` then `start_time`.

- [ ] **Step 3: Implement desktop/tablet/mobile timetable**

Desktop uses seven day columns and dynamic time rows. Tablet allows horizontal scrolling with a sticky time column. Mobile uses day pills and chronological event cards. Event links target `/classes/${class_id}`. Add loading, empty, and error states without removing current overview content.

- [ ] **Step 4: Verify responsive behavior at 375, 768, 1366, and 1920 widths**

Use the local browser companion when available; otherwise record manual browser screenshots and inspect overflow, click targets, and keyboard focus.

- [ ] **Step 5: Test, build, and commit**

```powershell
npm test -- src/features/timetable/weeklyTimetable.test.js
npm run build
git add frontend/src
git commit -m "feat: add responsive weekly timetable"
```

### Task 8: Implement Atomic Excel Template and Import APIs

**Files:**
- Modify: `java-backend/pom.xml`
- Modify: `java-backend/src/main/java/com/classmanagement/students/StudentController.java`
- Create: `java-backend/src/main/java/com/classmanagement/students/StudentImportService.java`
- Create: `java-backend/src/main/java/com/classmanagement/students/dto/StudentImportError.java`
- Create: `java-backend/src/main/java/com/classmanagement/students/dto/StudentImportResponse.java`
- Modify: `java-backend/src/main/java/com/classmanagement/domain/StudentRepository.java`
- Create: `java-backend/src/test/java/com/classmanagement/students/StudentImportServiceTest.java`

- [ ] **Step 1: Add Apache POI 5.5.1 and write RED workbook tests**

Tests create in-memory `XSSFWorkbook` instances for a valid file, missing required cells, invalid dates/gender/fee, duplicate file rows, database duplicates, unauthorized class, and a mixed valid/invalid file that must save zero rows.

- [ ] **Step 2: Implement class-scoped template download**

Add `GET /api/classes/{id}/students/import-template`. The workbook contains Vietnamese headers, date examples/formatting, gender data validation, and a short instruction sheet; it does not contain a class column.

- [ ] **Step 3: Implement normalization and validation without writes**

Normalize full name by trim/collapsed whitespace/lowercase and phone by removing whitespace, dots, and hyphens. Compare `(name,dob,phone)` against previous rows and existing records. Return every row/column/reason error together.

- [ ] **Step 4: Implement transactional import**

Add `POST /api/classes/{id}/students/import` as multipart upload. Only after zero validation errors, create active students, flush for IDs, assign codes, create enrollments using the row's enrollment date/custom fee, and write batch plus per-student audits.

- [ ] **Step 5: Verify rollback and generated codes, then commit**

```powershell
mvn -Dtest=StudentImportServiceTest test
mvn test
git add java-backend
git commit -m "feat: import class rosters from Excel"
```

### Task 9: Add Student Import UI and Student-Code Presentation

**Files:**
- Create: `frontend/src/pages/students/StudentImportPanel.jsx`
- Create: `frontend/src/pages/students/StudentImportPanel.test.jsx`
- Modify: `frontend/src/pages/students/StudentsPage.jsx`
- Modify: `frontend/src/pages/students/StudentDetailPage.jsx`
- Modify: `frontend/src/api.js`
- Modify: `frontend/src/format.js`

- [ ] **Step 1: Write RED component tests**

Verify class selection precedes template/upload actions, assistants see only assigned classes, multipart upload is used, structured row errors render, and a success message shows count/codes.

- [ ] **Step 2: Extend `api.js` for blob downloads and multipart bodies**

Do not force `Content-Type: application/json` for `FormData`; preserve JWT/error handling. Add a blob download helper that honors server filename headers.

- [ ] **Step 3: Implement the panel and student-code fields**

Use the existing drawer/modal and responsive form patterns. Refresh the student list after success. Add student code as a prioritized desktop column and mobile card field, show it on detail, and include it in search input guidance.

- [ ] **Step 4: Test, build, and commit**

```powershell
npm test -- src/pages/students/StudentImportPanel.test.jsx
npm run build
git add frontend/src
git commit -m "feat: add Excel student import workflow"
```

### Task 10: Centralize and Complete Mutation/Auth Auditing

**Files:**
- Modify: `java-backend/src/main/java/com/classmanagement/common/AuditService.java`
- Modify: `java-backend/src/main/java/com/classmanagement/auth/AuthService.java`
- Modify: `java-backend/src/main/java/com/classmanagement/user/UserService.java`
- Modify: `java-backend/src/main/java/com/classmanagement/classes/ClassService.java`
- Modify: `java-backend/src/main/java/com/classmanagement/sessions/SessionService.java`
- Modify: `java-backend/src/main/java/com/classmanagement/students/StudentService.java`
- Modify: `java-backend/src/main/java/com/classmanagement/attendance/AttendanceService.java`
- Modify: `java-backend/src/main/java/com/classmanagement/grades/GradeService.java`
- Modify: `java-backend/src/main/java/com/classmanagement/fees/FeeService.java`
- Create: `java-backend/src/test/java/com/classmanagement/audit/MutationAuditCoverageTest.java`
- Create: `java-backend/src/test/java/com/classmanagement/auth/AuthAuditTest.java`

- [ ] **Step 1: Write RED tests for categorized, secret-free events**

Assert login success/failure, password change, user/class/session/student/attendance/grade/fee mutations, transfer, and import produce the correct category/action/description. Assert serialized values contain neither supplied passwords nor issued JWTs.

- [ ] **Step 2: Expand the audit API**

Use `audit(category, tableName, recordId, action, description, oldValues, newValues, userId)` for domain changes. Add an isolated authentication-event method that can persist failure entries with null user/record IDs without logging secrets.

- [ ] **Step 3: Add missing calls to every successful mutation**

Always snapshot only business-relevant fields. Audit attendance on every create/update, not only late Admin corrections. Add create audits for grade columns/grades, student create/update/enroll/custom fee, class/staff/schedule/session operations, fee generation/adjustment/payment, and user lifecycle changes.

- [ ] **Step 4: Verify failed transactions do not leave success logs**

Use service tests that force repository/audit failure and assert rollback behavior.

- [ ] **Step 5: Run full backend suite and commit**

```powershell
mvn test
git add java-backend/src
git commit -m "feat: audit all state-changing operations"
```

### Task 11: Add the Admin Audit API and Responsive LOG Page

**Files:**
- Create: `java-backend/src/main/java/com/classmanagement/audit/AuditLogController.java`
- Create: `java-backend/src/main/java/com/classmanagement/audit/AuditLogService.java`
- Create: `java-backend/src/main/java/com/classmanagement/audit/dto/AuditLogDto.java`
- Create: `java-backend/src/test/java/com/classmanagement/audit/AuditLogQueryTest.java`
- Create: `frontend/src/pages/audit/AuditLogsPage.jsx`
- Create: `frontend/src/pages/audit/AuditLogsPage.css`
- Create: `frontend/src/pages/audit/AuditLogsPage.test.jsx`
- Modify: `frontend/src/App.jsx`
- Modify: `frontend/src/components/Layout.jsx`

- [ ] **Step 1: Write RED API tests for Admin-only filtering**

Test `GET /api/audit-logs` rejects non-Admin, paginates newest-first, and filters by category, user ID, action, date range, and text across description/table/action.

- [ ] **Step 2: Implement repository specifications, service DTO mapping, and controller**

Return actor name/email when available, safe parsed before/after JSON, total/page/page_size, and no mutation endpoints.

- [ ] **Step 3: Write RED frontend tests**

Assert only Admin sees navigation/route, filters update the request, selecting a row reveals before/after details, and mobile cards preserve every field.

- [ ] **Step 4: Implement `/logs` route/page**

Desktop uses a dense table with category badges and a detail drawer. Mobile uses expandable cards. Include loading, empty, error, permission-restricted, and pagination states.

- [ ] **Step 5: Test, build, and commit**

```powershell
mvn -Dtest=AuditLogQueryTest test
npm test -- src/pages/audit/AuditLogsPage.test.jsx
npm run build
git add java-backend/src frontend/src
git commit -m "feat: add categorized Admin audit log"
```

### Task 12: Full Regression, Local Data Verification, and Visual QA

**Files:**
- Modify only files required by failures discovered in this task.

- [ ] **Step 1: Run full automated verification**

```powershell
cd java-backend
mvn clean test
mvn -DskipTests package
cd ..\frontend
npm test
npm run build
```

Expected: zero test failures, backend package succeeds, frontend production build exits `0`.

- [ ] **Step 2: Start the local stack and verify migrations/API**

Verify `/api/health`, login for each role, student codes on existing rows, the weekly endpoint, Excel template/import rollback, and Admin audit filters against local SQL Server.

- [ ] **Step 3: Perform responsive workflow QA**

At approximately 375, 768, 1366, and 1920 pixels, verify attendance lock messages, grade-column controls, transfer, assistant fee summary, weekly timetable, Excel import errors/success, and LOG table/cards. Confirm keyboard navigation, focus visibility, touch targets, overflow, and no hidden essential actions.

- [ ] **Step 4: Inspect repository state and final diff**

Run `git status --short`, `git diff --check`, and review every changed migration, permission check, DTO, and UI capability against the approved spec.

- [ ] **Step 5: Commit any verification-only fixes and prepare handoff**

```powershell
git add java-backend/src java-backend/pom.xml frontend/src frontend/package.json frontend/package-lock.json
git commit -m "fix: address local workflow verification findings"
```

Do not push or publish. Report local URLs, test/build evidence, migration effects, default account implications, and any remaining operational caveats.
