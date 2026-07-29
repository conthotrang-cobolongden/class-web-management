# Audit Log Browser Design

## Goal

Provide administrators with a read-only, responsive audit-log browser backed by
`GET /api/audit-logs`. The feature must make existing audit records easy to
trace without exposing mutation endpoints or granting access to teachers and
assistants.

## Scope

The feature includes:

- An Admin-only, paginated audit-log query API.
- Combined filtering by category, actor email, action, inclusive time range,
  and keyword.
- Actor details and before/after JSON in each result.
- An Admin-only `/logs` route and navigation entry.
- A dense desktop table with a details drawer.
- Expandable mobile record cards.
- Loading, empty, error, retry, and permission-restricted states.
- Automated backend and frontend tests plus responsive local QA.

Editing, deleting, exporting, retention management, and real-time streaming are
outside this scope.

## Backend Architecture

### Query endpoint

`GET /api/audit-logs` accepts:

| Parameter | Meaning |
| --- | --- |
| `page` | One-based page number using the existing pagination contract. |
| `page_size` | Page size bounded by the existing pagination helper. |
| `category` | One of authentication, students, classes, sessions, attendance, grades, fees, users, imports. |
| `actor_email` | Case-insensitive partial match against the actor's email. |
| `action` | Exact audit action. |
| `from` | Inclusive ISO-8601 lower timestamp bound. |
| `to` | Inclusive ISO-8601 upper timestamp bound. |
| `q` | Case-insensitive partial match against description, table name, or action. |

Results use the existing `PageResponse` shape and are sorted by
`created_at DESC, id DESC` so pagination is stable when timestamps match.
Invalid pagination, category, action, or timestamps return a Vietnamese 400
response through the existing exception handler.

### Persistence and filtering

`AuditLogRepository` will also implement `JpaSpecificationExecutor`.
`AuditLog` will expose a read-only, optional relation to `User` through
`user_id`; writes continue to use the scalar `userId` field. A dedicated
`AuditLogService` composes Specifications only for supplied filters, performs
the paginated query, and maps entities to API DTOs.

This approach keeps one database-independent query model for both SQL Server
and PostgreSQL. No native query or vendor-specific migration is required.

### Response model

Each item includes:

- `id`, `category`, `action`, `description`
- `table_name`, `record_id`, `created_at`
- `actor` with `id`, `full_name`, and `email`, or null
- `old_values` and `new_values`

Snapshots are returned as real JSON values. If a legacy row contains malformed
JSON, its snapshot falls back to the original string so one bad row cannot
break an entire page.

### Authorization

`SecurityConfig` applies `hasAuthority("admin")` to
`/api/audit-logs/**`. The controller exposes GET only. No update or delete
route is introduced.

## Frontend Architecture

### Routing and navigation

`App.jsx` adds `/logs` behind the existing `RequireAdmin` wrapper.
`Layout.jsx` shows “Nhật ký” only for Admin users in both the desktop sidebar
and mobile sheet.

### Page composition

`AuditLogsPage` owns filter state, pagination, loading, errors, and the
selected record. Small focused components render:

- Category tabs.
- Filter controls for actor email, action, date range, and keyword.
- Desktop audit table.
- Desktop details drawer.
- Mobile expandable audit cards.
- Shared JSON snapshot views.

Actor email and keyword inputs are debounced. Changing any filter resets the
page to 1. Requests receive a sequence token or abort signal so stale responses
cannot replace newer results.

### Responsive behavior

- Up to 768px: audit records render as cards and expand inline.
- Above 768px: records render in an information-rich table.
- Selecting a desktop row opens a fixed right-side drawer with metadata and
  formatted before/after snapshots.
- The drawer becomes full-width where the available tablet width is limited.
- All interactive controls retain the existing touch-target and focus styles.

The page is strictly read-only and contains no edit or delete affordance.

### States

- Loading: skeleton rows or cards preserve page structure.
- Empty: explains that no logs match the current filters and offers filter
  reset.
- Error: displays the API message and a retry action.
- Permission restricted: a dedicated message is shown for HTTP 403, even
  though route protection normally prevents this state.

## Data Flow

1. The user changes a filter.
2. Debounced text values and immediate select/date values are normalized.
3. Local date boundaries are converted to ISO-8601 before calling the API.
4. The API validates filters and builds a Specification.
5. The repository loads one stable, newest-first page with optional actor data.
6. The service parses snapshots and returns DTOs.
7. The frontend discards stale responses and renders the current page.
8. Selecting a result displays the already-loaded record without another
   request.

## Testing

Backend tests cover:

- Admin access and non-Admin rejection.
- Stable newest-first ordering and pagination.
- Category, action, actor-email, keyword, and inclusive time filters.
- Multiple filters applied together.
- Invalid filter values.
- Actor-null records and malformed legacy snapshot fallback.

Frontend tests cover:

- Query-string construction and date conversion.
- Loading, empty, error, retry, and permission states.
- Filter reset and pagination behavior.
- Admin-only navigation and route protection.
- Desktop row selection/drawer rendering.
- Mobile card expansion.
- Safe JSON rendering without mutation controls.

Final verification runs:

- Full backend tests.
- Backend package.
- Full frontend tests.
- Frontend production build.
- Local migration and API smoke checks.
- Responsive checks at 375, 768, 1366, and 1920 pixels.

## Decisions

- Actor filtering is by case-insensitive partial email, not a user picker.
- Spring Data Specifications are used instead of JPQL condition matrices or
  vendor-specific native SQL.
- Time bounds are inclusive.
- Results are read-only and do not add export or retention behavior.
