# Adaptive Frontend Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved Warm Learning Studio adaptive redesign across every existing Class Management frontend route without changing APIs, authentication, authorization, calculations, or backend behavior.

**Architecture:** Keep data fetching, handlers, route guards, and business state inside the existing pages. Add a small presentation-only component layer and a token-driven CSS foundation, then adapt each page at mobile, tablet, desktop, and large-desktop breakpoints. Specialized attendance and grade layouts reuse existing page state and payloads.

**Tech Stack:** React 18, React Router 6, Recharts, Vite 5, plain CSS with custom properties.

---

## Protected files and interfaces

- Do not modify `java-backend/**`, `database/**`, `frontend/src/api.js`, or `frontend/src/auth.jsx`.
- Preserve route paths and guards in `frontend/src/App.jsx`.
- Preserve every endpoint, query parameter, JSON field, handler, validation rule, calculation, and permission check.
- Presentation components receive already-available values and callbacks; they do not fetch data or reinterpret business rules.

### Task 1: Establish the token-driven presentation foundation

**Files:**
- Modify: `frontend/src/styles.css`
- Create: `frontend/src/components/ui.jsx`
- Create: `frontend/src/components/ui.css`
- Modify: `frontend/src/main.jsx`

- [ ] **Step 1: Add a temporary static contract check**

Run before implementation:

```powershell
rg -n "--color-brand|export function PageHeader|export function StatePanel" frontend/src
```

Expected: no matches, establishing the new presentation layer does not exist yet.

- [ ] **Step 2: Implement semantic tokens**

Replace the root design variables with named primitives and semantics including:

```css
:root {
  --color-brand-700: #123b32;
  --color-brand-600: #184c40;
  --color-brand-500: #216354;
  --color-accent-500: #d8793e;
  --color-bg: #f7f4ed;
  --color-surface: #fffefb;
  --color-text: #1c2925;
  --color-text-muted: #65716c;
  --color-border: #dddcd4;
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 20px;
  --space-6: 24px;
  --radius-sm: 6px;
  --radius-md: 10px;
  --radius-lg: 14px;
  --control-sm: 36px;
  --control-md: 42px;
  --touch-target: 44px;
}
```

Retain compatibility aliases such as `--bg`, `--panel`, `--ink`, `--muted`, `--line`, and `--accent` while pages migrate.

- [ ] **Step 3: Create presentation-only primitives**

Implement `PageHeader`, `StatePanel`, `Pagination`, `StatusBadge`, `RecordCard`, `Overlay`, and `StickyActions` in `ui.jsx`. Each component accepts content/callback props only, forwards accessible attributes, and contains no API or permission logic.

- [ ] **Step 4: Add shared responsive component styles**

Define consistent page headers, field states, overlays, tables, record cards, pagination, sticky actions, focus rings, reduced motion, and mobile/tablet/desktop visibility helpers in `ui.css`.

- [ ] **Step 5: Import the shared styles and verify**

Run:

```powershell
npm run build
```

Expected: Vite build completes with no import or CSS parse errors.

### Task 2: Implement the adaptive application shell

**Files:**
- Modify: `frontend/src/components/Layout.jsx`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Preserve navigation contracts**

Record the existing destinations and role checks: `/`, `/users` for admin, `/classes`, `/students`, `/attendance`, `/grades`, `/fees`, `/reports`, `/doi-mat-khau`, and logout to `/login`.

- [ ] **Step 2: Restructure presentation markup**

Group desktop navigation into overview, teaching, and management sections; keep existing `NavLink` destinations and `user.role === 'admin'` checks. Add an accessible mobile header and secondary-action sheet while preserving current state and handlers.

- [ ] **Step 3: Implement breakpoint-specific shell behavior**

Use bottom navigation below 768px, a navigation rail at 768–1023px, and a persistent sidebar at 1024px and above. Keep content width controlled at 1440px and above.

- [ ] **Step 4: Verify routes still render**

Run the build and request `/login`, `/`, `/classes`, and `/students` from the Vite server. Expected: HTTP 200 for the SPA entry and no build failures.

### Task 3: Redesign authentication, dashboard, and password pages

**Files:**
- Modify: `frontend/src/pages/LoginPage.jsx`
- Modify: `frontend/src/pages/HomePage.jsx`
- Modify: `frontend/src/pages/home.css`
- Modify: `frontend/src/pages/ChangePasswordPage.jsx`

- [ ] **Step 1: Preserve login and password handlers**

Keep `login(email, password)`, navigation, loading, validation, and existing API errors unchanged.

- [ ] **Step 2: Apply the focused authentication form pattern**

Use a branded but restrained split layout on desktop and a full-height single-column mobile form with clear labels, error feedback, and loading button state.

- [ ] **Step 3: Recompose dashboard data**

Use only existing `dashboard`, `revenue`, and `classes` values. Place quick class actions first on mobile, balanced summaries on tablet, and metrics/charts/class summaries in a dense desktop grid.

- [ ] **Step 4: Apply the focused password form pattern**

Keep current fields and matching validation, add clear section context and success/error state styling.

- [ ] **Step 5: Build and smoke test role variants**

Login as the seeded admin and a seeded teaching user. Expected: admin dashboard metrics remain visible only to admin; assigned-class experience remains available to non-admin roles.

### Task 4: Redesign user, class, and student data-management pages

**Files:**
- Modify: `frontend/src/pages/UsersPage.jsx`
- Modify: `frontend/src/pages/classes/ClassesPage.jsx`
- Modify: `frontend/src/pages/classes/classes.css`
- Modify: `frontend/src/pages/students/StudentsPage.jsx`

- [ ] **Step 1: Keep queries and CRUD handlers unchanged**

Do not alter `/users`, `/classes`, `/students` calls, query parameters, create/edit bodies, pagination math, or activation confirmation behavior.

- [ ] **Step 2: Apply shared page headers and filters**

Use the same search/filter spacing, active-filter indication, clear action hierarchy, and mobile filter composition on all three pages.

- [ ] **Step 3: Add adaptive record presentations**

Retain desktop tables at 1024px and above. Add mobile record-card markup using the same item arrays and callbacks; show identity, primary status, prioritized metadata, detail link, and all current actions.

- [ ] **Step 4: Standardize create/edit overlays and forms**

Use the shared overlay shell and grouped fields without changing field names, required rules, handlers, or payloads.

- [ ] **Step 5: Verify search, filters, pagination, and CRUD presentation**

Exercise each page at 390px and 1280px. Expected: the same API requests fire, all actions remain available, and no table is compressed into unreadable mobile columns.

### Task 5: Redesign class and student detail workspaces

**Files:**
- Modify: `frontend/src/pages/classes/ClassDetailPage.jsx`
- Modify: `frontend/src/pages/classes/classes.css`
- Modify: `frontend/src/pages/students/StudentDetailPage.jsx`

- [ ] **Step 1: Preserve detail workflows**

Keep class update, staff assignment, schedules, session generation/cancel/makeup, roster links, profile update, transfer, enrollment, custom fee, and history calls unchanged.

- [ ] **Step 2: Recompose class detail**

Create a summary header and responsive sections for information, staff, weekly schedule, sessions, and roster. Use accordions/stacked sections on mobile, balanced panels on tablet, and a multi-column workspace on desktop.

- [ ] **Step 3: Recompose student detail**

Create a profile summary and responsive sections for profile, current enrollment, actions, custom fee, and class history. Preserve each existing form panel and its callbacks.

- [ ] **Step 4: Standardize sensitive action confirmation styling**

Keep existing confirmation logic and text; visually distinguish transfer, cancellation, and other high-consequence actions.

- [ ] **Step 5: Build and exercise representative records**

Open a real class and student from seeded data at 390, 768, and 1280px. Expected: every section and action is reachable with no overflow.

### Task 6: Implement the rapid attendance workflow

**Files:**
- Modify: `frontend/src/pages/attendance/AttendancePage.jsx`
- Modify: `frontend/src/pages/attendance/attendance.css`

- [ ] **Step 1: Preserve attendance state and payload**

Keep class/session requests, four statuses, roster notes, locked state, changed-row detection, and save body unchanged.

- [ ] **Step 2: Add explicit workflow context**

Show selected class/session, editability, marked count, and unsaved count using derived presentation values only.

- [ ] **Step 3: Implement mobile roster cards**

Render each existing roster item as a card below 768px with 44px status controls, note access, clear selected text/state, and sticky save actions.

- [ ] **Step 4: Implement tablet and desktop roster layouts**

Use compact touch rows on tablet and a sticky-header table on desktop. Keep all statuses and notes visible/reachable.

- [ ] **Step 5: Verify save and lock behavior**

Change statuses, correct a mistake, save, reload, and open a locked session. Expected: payload and backend outcomes match baseline; locked controls remain disabled.

### Task 7: Implement adaptive grade entry

**Files:**
- Modify: `frontend/src/pages/grades/GradesPage.jsx`
- Modify: `frontend/src/pages/grades/GradesPage.css`

- [ ] **Step 1: Preserve grade contracts**

Keep column CRUD, score validation, changed-count detection, weighted averages, save payload, export request, and unsaved class-change confirmation unchanged.

- [ ] **Step 2: Improve state visibility**

Use explicit saved, unsaved, invalid, loading, and failed cell styling derived from existing state.

- [ ] **Step 3: Implement mobile focused entry**

Use numeric input hints, a compact student/column context, and previous/next presentation controls over the existing matrix without altering values or calculations.

- [ ] **Step 4: Implement tablet/desktop grade grids**

Use horizontal overflow where unavoidable, sticky student names and header, readable column widths, and always-visible save/export/column actions.

- [ ] **Step 5: Verify editing, validation, discard confirmation, and export**

Enter valid/invalid scores, switch class with unsaved data, save a batch, and export CSV. Expected: existing confirmations, validation, and results are preserved.

### Task 8: Redesign fees and reports

**Files:**
- Modify: `frontend/src/pages/fees/FeesPage.jsx`
- Modify: `frontend/src/pages/fees/fees.css`
- Modify: `frontend/src/pages/reports/ReportsPage.jsx`
- Modify: `frontend/src/pages/reports/reports.css`
- Modify: `frontend/src/charts/theme.js`

- [ ] **Step 1: Preserve role-dependent finance behavior**

Keep fee generation, payment, adjustment, payment history, revenue visibility, report filters, and API requests unchanged.

- [ ] **Step 2: Apply adaptive fee presentation**

Use fee cards and mobile-safe sheets below 768px; retain information-rich table rows, expandable payment history, summaries, filters, and pagination on desktop.

- [ ] **Step 3: Apply adaptive report composition**

Stack summaries/charts/tables on mobile, pair panels on tablet, and use multi-column reporting on desktop. Update Recharts colors to approved semantic tokens only.

- [ ] **Step 4: Verify permissions and data states**

Exercise admin and non-admin roles, empty filters, loading, API error, payment history, and pagination. Expected: revenue remains hidden where currently restricted.

### Task 9: Accessibility and responsive hardening

**Files:**
- Modify: presentation files from Tasks 1–8 as needed

- [ ] **Step 1: Audit labels and dialog semantics**

Ensure inputs retain labels, icon buttons have accessible names, overlays expose dialog titles, and heading order is coherent.

- [ ] **Step 2: Audit focus, touch targets, and motion**

Verify visible `:focus-visible`, 44px mobile controls, safe overlay scrolling, and `prefers-reduced-motion` behavior.

- [ ] **Step 3: Audit representative widths**

Check 360, 390, 480, 768, 1024, 1280, 1440, and 1920px. Expected: no unintended horizontal page overflow; only explicit table/grade scrollers overflow.

- [ ] **Step 4: Fix console and runtime issues**

Navigate every route and resolve new React warnings, missing keys, invalid DOM nesting, and CSS overflow issues.

### Task 10: Final verification and protection audit

**Files:**
- No planned source additions beyond Tasks 1–9

- [ ] **Step 1: Run the production build**

```powershell
npm run build
```

Expected: exit code 0.

- [ ] **Step 2: Record unavailable automated checks**

Inspect `package.json`. Expected baseline: no `lint`, `typecheck`, or `test` scripts. Do not claim these ran.

- [ ] **Step 3: Smoke-test every route and key workflow**

Use the running backend and frontend to verify login, navigation, management lists/details, search, filters, pagination, forms, confirmations, attendance, grades, fees, reports, and password change.

- [ ] **Step 4: Review the final diff**

```powershell
git status --short
git diff --check
git diff --name-only HEAD~1
```

Expected: only design docs and frontend presentation files; no backend, database, API contract, authentication, authorization, or business-logic file changes.

- [ ] **Step 5: Commit the implementation**

```powershell
git add frontend docs/superpowers/plans/2026-07-28-adaptive-frontend-redesign.md
git commit -m "feat: redesign class management frontend"
```
