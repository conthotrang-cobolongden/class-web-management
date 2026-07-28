# Adaptive Frontend Redesign Design

## Objective

Redesign the complete React frontend in `C:\Users\Admin\Downloads\class web management` using the approved **Warm Learning Studio** visual direction and **Adaptive workflow-first** responsive strategy. Preserve every existing route, API call, payload, handler, validation rule, permission, calculation, and business workflow.

## Scope and protection boundary

Frontend presentation files under `frontend/src` are in scope. New frontend-only components and CSS files may be added.

The following are immutable for this redesign:

- `java-backend/**`, `database/**`, migrations, database configuration, server configuration.
- `frontend/src/api.js` request/response contracts and session behavior.
- `frontend/src/auth.jsx` authentication and authorization behavior.
- Routes and role guards in `frontend/src/App.jsx`.
- API field names, URLs, query behavior, payload shapes, calculations, permissions, and business rules.
- Existing form handlers, CRUD operations, search, filters, sorting, pagination, exports, error handling, and loading behavior.

## Existing application inventory

| Route | Page | Existing capabilities to preserve |
|---|---|---|
| `/login` | Login | Email/password login, error and loading feedback |
| `/` | Dashboard | Admin metrics, revenue history, grade distribution, assigned-class cards and links |
| `/users` | User management | Admin-only search/filter, pagination, create, edit, activate/deactivate |
| `/classes` | Class management | Search/filter, pagination, create class, detail navigation |
| `/classes/:id` | Class detail | Class metadata, staff assignment, schedules, generated sessions, cancel/makeup session flows, roster |
| `/students` | Student management | Search/filter, pagination, create, detail navigation |
| `/students/:id` | Student detail | Profile editing, enrollment, transfer, custom fee, class history |
| `/attendance` | Attendance | Class and session selection, four existing statuses, notes, locked state, batch save |
| `/grades` | Grade entry | Class selection, grade columns, weighted average, batch editing, validation, unsaved confirmation, CSV export |
| `/fees` | Fees | Month/class/status filters, generation, adjustment, payment, history expansion, pagination |
| `/reports` | Reports | Attendance and revenue tabs, filters, charts, summary and detail tables |
| `/doi-mat-khau` | Password change | Current/new/confirmation fields, validation, error and success feedback |

## Design direction

Warm Learning Studio is professional, calm, and human without decorative excess.

- Primary forest: `#184C40`; primary hover: `#123B32`; secondary forest: `#216354`.
- Clay accent: `#D8793E`, used sparingly for emphasis and unsaved states.
- Cream application background: `#F7F4ED`; paper surface: `#FFFEFB`.
- Ink: `#1C2925`; secondary text: `#65716C`; border: `#DDDCD4`.
- Semantic colors retain clear success, warning, danger, and informational meaning and never rely on color alone.
- Typography uses the existing system font stack, a restrained type scale, readable line heights, and consistent weights.
- Radius scale is compact (`6`, `8`, `10`, `12`, `16`); shadows are subtle and reserved for overlays or elevated navigation.
- Motion is limited to fast drawers, sheets, dialogs, accordions, toasts, and state transitions, with reduced-motion support.

## Responsive model

- Small mobile: below 480px.
- Mobile: 480–767px.
- Tablet: 768–1023px.
- Laptop/desktop: 1024–1439px.
- Large desktop: 1440px and above.

Breakpoints express task needs, not simple scaling.

### Mobile

- Single-column task composition and minimum 44px touch targets.
- Compact header, high-frequency bottom navigation, and an organized secondary-action sheet.
- Sticky context and action bars for attendance, grades, and long forms.
- Search first; filters in an accessible sheet or collapsible panel.
- Dense records become priority-field cards with expandable details and complete action access.
- Essential functionality remains available; horizontal scrolling is used only for genuinely spreadsheet-like data.

### Tablet

- Collapsible navigation rail and balanced list/detail or two-column layouts.
- Touch-friendly controls in both orientations.
- Priority tables show more columns than mobile while preserving clear detail access.
- Forms use two columns only for naturally paired fields.

### Desktop

- Persistent grouped sidebar, contextual top bar, dense readable tables, inline filters, pagination, and multi-column workspaces.
- Sticky table headers and identifier columns where useful.
- Forms and details use controlled-width section grids rather than oversized cards.

### Large desktop

- Content is capped at a productive width or gains useful side summaries/details.
- Text fields and rows do not stretch across empty space.
- Reports and long tables may expose additional supported columns without inventing data.

## Shared frontend primitives

Create reusable presentation components while retaining page-owned data and handlers:

- `PageHeader`: title, description, breadcrumbs/context, actions.
- Shared button classes: primary, secondary, ghost, danger, small, icon plus label, loading, disabled.
- Shared field classes: label, required marker, helper, error, disabled, control sizing.
- `FilterBar`: desktop inline filters and mobile filter trigger/active count.
- `DataTable`: consistent shell, header, row hover/selected state, overflow container, sticky support.
- `RecordCard`: mobile equivalent that prioritizes identity/status and exposes all details/actions.
- `StatusBadge`: text plus semantic color.
- `Pagination`: consistent counts, current page, previous/next, disabled state.
- `StatePanel`: loading, empty, no-results, error/retry, restricted, and success variants.
- `Overlay`: shared modal/drawer/bottom-sheet structure with title, close, scroll body, actions, and focus-safe semantics.
- `StickyActions`: mobile-safe primary/secondary action bar.
- `UnsavedIndicator`: explicit unsaved count/state used by attendance and grades.

No new runtime dependency is required; existing React, React Router, Recharts, and CSS remain the stack.

## Workflow designs

### Attendance

- Preserve class selection, date range/week controls, session selection, roster loading, lock behavior, four statuses, notes, and save payload.
- Mobile uses student cards with four large segmented status controls, search, sticky class/session context, progress, unsaved count, and prominent save.
- Tablet uses roster/detail split or compact touch rows.
- Desktop uses a full roster table with sticky header, visible notes, progress, and batch save.
- Accidental edits remain correctable before save. Existing unsaved navigation protection is retained and made visually explicit.

### Grades

- Preserve class selection, column CRUD, score ranges, weighted average, batch payload, export, and unsaved confirmation.
- Mobile uses focused student/column entry with numeric keyboard hints, previous/next navigation using existing data, score range help, and saved/unsaved/error states.
- Tablet uses a priority grade grid with sticky student identity and horizontal scroll where necessary.
- Desktop uses spreadsheet-like inputs, keyboard-friendly traversal, sticky header/student column, column management, export, and batch save.
- No score calculation or allowed range changes.

### Forms

- Use one label/helper/error model for login, passwords, users, classes, schedules, students, enrollment, transfers, fee adjustments/payments, and grade columns.
- Group existing fields into semantic sections; never rename fields or change payloads.
- Mobile uses one column and sticky actions for long forms. Tablet/desktop may use two columns for natural pairs.
- Validation is adjacent to its field, with a summary only when useful. Existing browser and handler validation remains authoritative.

### Data management

- Users, classes, students, fees, reports, rosters, sessions, and history tables share consistent search, filters, active-filter indication, reset, sorting affordances where supported, pagination, row actions, and state language.
- Desktop retains information-rich tables and existing filters/actions.
- Mobile uses record cards, expandable detail, drawers/sheets for secondary actions, and prioritized fields without removing any essential field/action.
- Bulk UI is shown only where an existing operation can act on multiple records; no unsupported bulk backend behavior is introduced.

## Page composition

- Login: focused branded authentication surface; readable error/loading states.
- Dashboard: mobile quick links and key summaries first; desktop metrics, charts, and class summaries in a dense grid using only existing API data.
- Users: responsive account cards/table with create/edit overlays and activation confirmations.
- Classes: responsive class cards/table; detail page groups information, staff, schedule, sessions, and roster into navigable sections.
- Students: fast mobile lookup cards, tablet list/detail, desktop table; detail page uses profile summary and sectioned panels.
- Attendance and grades follow the specialized workflows above.
- Fees: mobile fee cards and bottom-sheet payment/adjustment forms; desktop summary, filters, expandable payment history, and pagination.
- Reports: stacked mobile summaries, tablet paired panels, desktop multi-column charts/tables; role-based revenue visibility is unchanged.
- Password change: narrow, accessible form with explicit validation/success feedback.

## States and accessibility

- Every data surface includes intentional initial loading, background loading, empty, no-results, error/retry, disabled, success, missing-data, and permission-restricted presentation where existing logic exposes it.
- Controls have visible focus states, semantic labels, accessible names, and minimum touch size on touch layouts.
- Dialogs/sheets use accessible dialog semantics, close controls, safe internal scrolling, and keyboard dismissal where implementation permits.
- Status is communicated with icon/text plus color.
- Layouts tolerate long Vietnamese names, labels, emails, and values without overflow.

## Verification design

- Baseline and final build: `npm run build` in `frontend`.
- The project has no lint, type-check, or frontend test scripts; report these as unavailable unless added specifically for presentation behavior.
- Test routes at 360, 390, 480, 768, 1024, 1280, 1440, and 1920px, including tablet portrait and landscape where practical.
- Exercise login, navigation, class/student/user presentation, search, filters, pagination, attendance, grades, forms, confirmations, fees, reports, loading, empty, and error states against the existing backend.
- Review browser console, overflow, focus visibility, touch sizes, and unsaved/save feedback.
- Final `git diff --name-only` must contain no `java-backend/**`, `database/**`, API/auth/route-contract, or business-logic changes.

## Approved visual references

The browser visual companion established two approved visual sections:

1. Warm Learning Studio foundations and adaptive navigation shell.
2. Unified attendance, grade, form, and data-management patterns with route coverage.

These references and this specification are the source for the editable Figma file and frontend implementation.
