# Audit Log Browser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Admin-only, paginated audit-log API and a responsive, read-only `/logs` page with combined filters and before/after details.

**Architecture:** Spring Data JPA Specifications compose portable filters over `AuditLog` and its optional read-only actor relation. A dedicated service validates query parameters, parses JSON snapshots, and returns the existing page contract. React separates query construction from presentation, ignores stale requests, and switches between a desktop table/drawer and expandable mobile cards through CSS.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Jackson, JUnit 5, Mockito, React 18, React Router 6, Vitest, Testing Library, CSS.

---

## File Map

### Backend

- Create `java-backend/src/main/java/com/classmanagement/audit/AuditLogController.java`: GET-only HTTP contract.
- Create `java-backend/src/main/java/com/classmanagement/audit/AuditLogFilter.java`: validated filter value object.
- Create `java-backend/src/main/java/com/classmanagement/audit/AuditLogService.java`: validation, Specification composition, pagination, and DTO mapping.
- Create `java-backend/src/main/java/com/classmanagement/audit/AuditLogSpecifications.java`: one focused predicate factory per filter.
- Create `java-backend/src/main/java/com/classmanagement/audit/dto/AuditActorDto.java`: safe actor projection.
- Create `java-backend/src/main/java/com/classmanagement/audit/dto/AuditLogDto.java`: API record including parsed snapshots.
- Modify `java-backend/src/main/java/com/classmanagement/domain/AuditLog.java`: optional read-only actor relation.
- Modify `java-backend/src/main/java/com/classmanagement/domain/AuditLogRepository.java`: add `JpaSpecificationExecutor`.
- Modify `java-backend/src/main/java/com/classmanagement/config/SecurityConfig.java`: Admin-only API matcher.
- Create `java-backend/src/test/java/com/classmanagement/audit/AuditLogServiceTest.java`: validation, mapping, malformed JSON, sort contract.
- Create `java-backend/src/test/java/com/classmanagement/audit/AuditLogSpecificationsTest.java`: real Criteria behavior for combined filters.
- Create `java-backend/src/test/java/com/classmanagement/audit/AuditLogControllerSecurityTest.java`: Admin/teacher/unauthenticated access.

### Frontend

- Create `frontend/src/features/audit/auditQuery.js`: filter normalization and query-string construction.
- Create `frontend/src/features/audit/auditQuery.test.js`: date boundaries and omitted-empty-filter tests.
- Create `frontend/src/pages/audit/AuditLogsPage.jsx`: data state, stale-request protection, filters, paging, and states.
- Create `frontend/src/pages/audit/AuditLogsPage.test.jsx`: loading, results, empty, error, permission, filtering, paging, drawer, and cards.
- Create `frontend/src/pages/audit/AuditLogDetails.jsx`: shared metadata and safe snapshot rendering.
- Create `frontend/src/pages/audit/audit-logs.css`: desktop table/drawer and mobile cards.
- Modify `frontend/src/App.jsx`: Admin route.
- Modify `frontend/src/components/Layout.jsx`: Admin-only navigation.
- Create `frontend/src/components/Layout.audit.test.jsx`: navigation visibility.

## Task 1: Backend Query, Filter Contract, and DTO Mapping

**Files:**

- Create: `java-backend/src/main/java/com/classmanagement/audit/AuditLogFilter.java`
- Create: `java-backend/src/main/java/com/classmanagement/audit/AuditLogSpecifications.java`
- Create: `java-backend/src/main/java/com/classmanagement/audit/AuditLogService.java`
- Create: `java-backend/src/main/java/com/classmanagement/audit/dto/AuditActorDto.java`
- Create: `java-backend/src/main/java/com/classmanagement/audit/dto/AuditLogDto.java`
- Modify: `java-backend/src/main/java/com/classmanagement/domain/AuditLog.java`
- Modify: `java-backend/src/main/java/com/classmanagement/domain/AuditLogRepository.java`
- Modify: `java-backend/pom.xml`
- Test: `java-backend/src/test/java/com/classmanagement/audit/AuditLogServiceTest.java`
- Test: `java-backend/src/test/java/com/classmanagement/audit/AuditLogSpecificationsTest.java`

- [ ] **Step 1: Write failing service and JPA tests**

Create service tests for valid mapping, stable sort, invalid category/action/time
range, a null actor, and malformed snapshot fallback:

```java
@Test
void returnsNewestFirstPageWithParsedSnapshotsAndActor() {
    AuditLog log = auditLog(9L, "fees", "create", "{\"amount\":500000}", actor());
    when(repository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));

    PageResponse<AuditLogDto> result = service.list(
            Pagination.of("1", "20"),
            new AuditLogFilter("fees", "admin@", "create", null, null, "payment"));

    assertEquals(1, result.total());
    assertEquals("admin@test.local", result.items().getFirst().actor().email());
    assertEquals(500000, result.items().getFirst().newValues().get("amount").asInt());
    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findAll(any(Specification.class), pageable.capture());
    assertEquals(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
            pageable.getValue().getSort());
}

@Test
void preservesMalformedLegacySnapshotAsTextNode() {
    AuditLog log = auditLog(9L, "fees", "create", "{broken", null);
    when(repository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(log)));

    AuditLogDto item = service.list(
            Pagination.of(null, null), AuditLogFilter.empty()).items().getFirst();

    assertTrue(item.newValues().isTextual());
    assertEquals("{broken", item.newValues().textValue());
    assertNull(item.actor());
}

@ParameterizedTest
@ValueSource(strings = {"unknown", "payments"})
void rejectsUnknownCategory(String category) {
    ApiException error = assertThrows(ApiException.class, () -> service.list(
            Pagination.of(null, null),
            new AuditLogFilter(category, null, null, null, null, null)));
    assertEquals(400, error.getStatus());
}
```

Use these unit-test helpers to create the entity graph without adding
production setters:

```java
private User actor() {
    User user = new User();
    ReflectionTestUtils.setField(user, "id", 1L);
    user.setFullName("Admin");
    user.setEmail("admin@test.local");
    return user;
}

private AuditLog auditLog(Long id, String category, String action,
                          String newValues, User actor) {
    AuditLog log = new AuditLog();
    ReflectionTestUtils.setField(log, "id", id);
    ReflectionTestUtils.setField(log, "createdAt",
            Instant.parse("2026-07-29T01:00:00Z"));
    ReflectionTestUtils.setField(log, "actor", actor);
    log.setCategory(category);
    log.setAction(action);
    log.setTableName("payments");
    log.setNewValues(newValues);
    log.setUserId(actor == null ? null : actor.getId());
    return log;
}
```

Create a real Criteria integration test:

```java
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuditLogSpecificationsTest {

    @Autowired AuditLogRepository repository;
    @Autowired TestEntityManager entities;

    @Test
    void combinesActorEmailCategoryActionKeywordAndInclusiveTime() {
        User actor = persistUser("Admin One", "ADMIN@Test.Local");
        persistLog("fees", "create", "Ghi nhận payment", "payments", actor,
                Instant.parse("2026-07-29T01:00:00Z"));
        persistLog("students", "update", "Cập nhật hồ sơ", "students", null,
                Instant.parse("2026-07-29T02:00:00Z"));

        AuditLogFilter filter = new AuditLogFilter(
                "fees", "admin@test", "create",
                Instant.parse("2026-07-29T01:00:00Z"),
                Instant.parse("2026-07-29T01:00:00Z"), "PAYMENT");

        List<AuditLog> result = repository.findAll(AuditLogSpecifications.from(filter));

        assertEquals(1, result.size());
        assertEquals("ADMIN@Test.Local", result.getFirst().getActor().getEmail());
    }
}
```

Use these concrete helpers so the read-only actor relation is populated through
`userId`:

```java
private User persistUser(String name, String email) {
    User user = new User();
    user.setFullName(name);
    user.setEmail(email);
    user.setPasswordHash("bcrypt-test-hash");
    user.setRole("admin");
    return entities.persistAndFlush(user);
}

private AuditLog persistLog(String category, String action, String description,
                            String tableName, User actor, Instant createdAt) {
    AuditLog log = new AuditLog();
    log.setCategory(category);
    log.setAction(action);
    log.setDescription(description);
    log.setTableName(tableName);
    log.setUserId(actor == null ? null : actor.getId());
    ReflectionTestUtils.setField(log, "createdAt", createdAt);
    return entities.persistAndFlush(log);
}
```

- [ ] **Step 2: Run both tests to verify RED**

Run:

```powershell
cd java-backend
mvn.cmd '-Dtest=AuditLogServiceTest,AuditLogSpecificationsTest' test
```

Expected: compilation fails because the service, DTOs, Specification factory,
actor relation, and Specification-capable repository do not exist.

- [ ] **Step 3: Add filter and DTO types**

```java
public record AuditLogFilter(
        String category, String actorEmail, String action,
        Instant from, Instant to, String keyword) {
    public static AuditLogFilter empty() {
        return new AuditLogFilter(null, null, null, null, null, null);
    }
}

public record AuditActorDto(Long id, String fullName, String email) {
    public static AuditActorDto from(User user) {
        return user == null ? null : new AuditActorDto(
                user.getId(), user.getFullName(), user.getEmail());
    }
}

public record AuditLogDto(
        Long id, String category, String action, String description,
        String tableName, Long recordId, Instant createdAt,
        AuditActorDto actor, JsonNode oldValues, JsonNode newValues) {
}
```

- [ ] **Step 4: Add persistence support**

Add H2 to `pom.xml`:

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

Modify `AuditLog`:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", insertable = false, updatable = false)
private User actor;

@PrePersist
void onCreate() {
    if (createdAt == null) {
        createdAt = Instant.now();
    }
}

public User getActor() {
    return actor;
}
```

Modify `AuditLogRepository`:

```java
public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByTableNameAndRecordIdOrderByIdDesc(
            String tableName, Long recordId);
}
```

- [ ] **Step 5: Implement portable predicates**

```java
public static Specification<AuditLog> from(AuditLogFilter filter) {
    return Specification.allOf(
            equal("category", filter.category()),
            actorEmailContains(filter.actorEmail()),
            equal("action", filter.action()),
            atOrAfter(filter.from()),
            atOrBefore(filter.to()),
            keywordContains(filter.keyword()));
}

private static Specification<AuditLog> actorEmailContains(String email) {
    return (root, query, cb) -> {
        if (!hasText(email)) return cb.conjunction();
        Join<AuditLog, User> actor = root.join("actor", JoinType.LEFT);
        return cb.like(cb.lower(actor.get("email")),
                "%" + email.trim().toLowerCase(Locale.ROOT) + "%");
    };
}

private static Specification<AuditLog> keywordContains(String keyword) {
    return (root, query, cb) -> {
        if (!hasText(keyword)) return cb.conjunction();
        String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        return cb.or(
                cb.like(cb.lower(root.get("description")), pattern),
                cb.like(cb.lower(root.get("tableName")), pattern),
                cb.like(cb.lower(root.get("action")), pattern));
    };
}
```

```java
private static Specification<AuditLog> equal(String field, String value) {
    return (root, query, cb) -> hasText(value)
            ? cb.equal(root.get(field), value.trim())
            : cb.conjunction();
}

private static Specification<AuditLog> atOrAfter(Instant value) {
    return (root, query, cb) -> value == null
            ? cb.conjunction()
            : cb.greaterThanOrEqualTo(root.get("createdAt"), value);
}

private static Specification<AuditLog> atOrBefore(Instant value) {
    return (root, query, cb) -> value == null
            ? cb.conjunction()
            : cb.lessThanOrEqualTo(root.get("createdAt"), value);
}

private static boolean hasText(String value) {
    return value != null && !value.isBlank();
}
```

- [ ] **Step 6: Implement service validation and mapping**

```java
private static final Set<String> CATEGORIES = Set.of(
        "authentication", "students", "classes", "sessions", "attendance",
        "grades", "fees", "users", "imports");
private static final Set<String> ACTIONS = Set.of(
        "create", "update", "delete", "login_success", "login_failure", "import");
private static final Sort NEWEST_FIRST = Sort.by(
        Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

@Transactional(readOnly = true)
public PageResponse<AuditLogDto> list(Pagination pagination, AuditLogFilter filter) {
    validate(filter);
    Pageable pageable = PageRequest.of(
            pagination.page() - 1, pagination.pageSize(), NEWEST_FIRST);
    Page<AuditLog> result = repository.findAll(
            AuditLogSpecifications.from(filter), pageable);
    return new PageResponse<>(
            result.getContent().stream().map(this::toDto).toList(),
            result.getTotalElements(), pagination.page(), pagination.pageSize());
}

private JsonNode snapshot(String json) {
    if (json == null) return null;
    try {
        return objectMapper.readTree(json);
    } catch (JsonProcessingException ignored) {
        return TextNode.valueOf(json);
    }
}
```

```java
private void validate(AuditLogFilter filter) {
    if (filter.category() != null && !CATEGORIES.contains(filter.category())) {
        throw new ApiException(400, "Category audit không hợp lệ");
    }
    if (filter.action() != null && !ACTIONS.contains(filter.action())) {
        throw new ApiException(400, "Action audit không hợp lệ");
    }
    if (filter.from() != null && filter.to() != null
            && filter.from().isAfter(filter.to())) {
        throw new ApiException(400,
                "Thời gian bắt đầu phải trước thời gian kết thúc");
    }
}

private AuditLogDto toDto(AuditLog log) {
    return new AuditLogDto(
            log.getId(), log.getCategory(), log.getAction(), log.getDescription(),
            log.getTableName(), log.getRecordId(), log.getCreatedAt(),
            AuditActorDto.from(log.getActor()),
            snapshot(log.getOldValues()), snapshot(log.getNewValues()));
}
```

- [ ] **Step 7: Run query tests to verify GREEN**

Run:

```powershell
mvn.cmd '-Dtest=AuditLogServiceTest,AuditLogSpecificationsTest' test
```

Expected: both classes pass, including the inclusive time boundary.

- [ ] **Step 8: Commit the backend query**

```powershell
git add java-backend/pom.xml java-backend/src/main/java/com/classmanagement/audit java-backend/src/main/java/com/classmanagement/domain/AuditLog.java java-backend/src/main/java/com/classmanagement/domain/AuditLogRepository.java java-backend/src/test/java/com/classmanagement/audit/AuditLogServiceTest.java java-backend/src/test/java/com/classmanagement/audit/AuditLogSpecificationsTest.java
git commit -m "feat: query and filter audit logs"
```
## Task 2: Admin-Only Audit Log Endpoint

**Files:**

- Create: `java-backend/src/main/java/com/classmanagement/audit/AuditLogController.java`
- Modify: `java-backend/src/main/java/com/classmanagement/config/SecurityConfig.java`
- Test: `java-backend/src/test/java/com/classmanagement/audit/AuditLogControllerSecurityTest.java`

- [ ] **Step 1: Write failing MockMvc security and contract tests**

```java
@WebMvcTest(AuditLogController.class)
@Import(SecurityConfig.class)
class AuditLogControllerSecurityTest {

    @Autowired MockMvc mvc;
    @MockitoBean AuditLogService service;
    @MockitoBean JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void forwardJwtFilter() throws Exception {
        doAnswer(call -> {
            ServletRequest request = call.getArgument(0);
            ServletResponse response = call.getArgument(1);
            FilterChain chain = call.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(authorities = "admin")
    void adminCanQueryFilters() throws Exception {
        when(service.list(any(), any())).thenReturn(new PageResponse<>(
                List.of(), 0, 1, 20));
        mvc.perform(get("/api/audit-logs")
                        .param("actor_email", "admin@")
                        .param("category", "fees")
                        .param("from", "2026-07-29T00:00:00Z")
                        .param("to", "2026-07-29T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page_size").value(20));
    }

    @Test
    @WithMockUser(authorities = "teacher")
    void teacherIsForbidden() throws Exception {
        mvc.perform(get("/api/audit-logs")).andExpect(status().isForbidden());
    }
    @Test
    void unauthenticatedUserIsRejected() throws Exception {
        mvc.perform(get("/api/audit-logs")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "admin")
    void mutationMethodIsRejected() throws Exception {
        mvc.perform(post("/api/audit-logs")).andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run controller security tests to verify RED**

Run:

```powershell
mvn.cmd -Dtest=AuditLogControllerSecurityTest test
```

Expected: compilation fails because the controller does not exist.

- [ ] **Step 3: Implement GET-only controller parsing**

```java
@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditLogService service;

    @GetMapping
    public PageResponse<AuditLogDto> list(
            @RequestParam(required = false) String page,
            @RequestParam(name = "page_size", required = false) String pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(name = "actor_email", required = false) String actorEmail,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(name = "q", required = false) String keyword) {
        AuditLogFilter filter = new AuditLogFilter(
                emptyToNull(category), emptyToNull(actorEmail), emptyToNull(action),
                parseInstant("from", from), parseInstant("to", to), emptyToNull(keyword));
        return service.list(Pagination.of(page, pageSize), filter);
    }
}
```

```java
private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
}

private static Instant parseInstant(String field, String value) {
    if (value == null || value.isBlank()) return null;
    try {
        return Instant.parse(value);
    } catch (DateTimeParseException error) {
        throw new ApiException(400,
                "Thời gian " + field + " không đúng định dạng ISO-8601");
    }
}
```

- [ ] **Step 4: Add the Admin security matcher before the generic API matcher**

```java
.requestMatchers("/api/users/**").hasAuthority("admin")
.requestMatchers(HttpMethod.GET, "/api/audit-logs/**").hasAuthority("admin")
.requestMatchers("/api/audit-logs/**").denyAll()
.requestMatchers("/api/**").authenticated()
```

The `denyAll` rule guarantees POST/PUT/DELETE remain unavailable even if a
controller is accidentally added later.

- [ ] **Step 5: Run endpoint tests and full backend suite**

Run:

```powershell
mvn.cmd -Dtest=AuditLogControllerSecurityTest test
mvn.cmd test
```

Expected: endpoint security tests pass; full backend suite has zero failures.

- [ ] **Step 6: Commit the API**

```powershell
git add java-backend/src/main/java/com/classmanagement/audit/AuditLogController.java java-backend/src/main/java/com/classmanagement/config/SecurityConfig.java java-backend/src/test/java/com/classmanagement/audit/AuditLogControllerSecurityTest.java
git commit -m "feat: expose admin audit log API"
```

## Task 3: Frontend Query Contract

**Files:**

- Create: `frontend/src/features/audit/auditQuery.js`
- Create: `frontend/src/features/audit/auditQuery.test.js`

- [ ] **Step 1: Write failing query-builder tests**

```javascript
import { describe, expect, it } from 'vitest';
import { buildAuditQuery, localDateBounds } from './auditQuery.js';

describe('audit query', () => {
  it('omits empty filters and sends selected filters', () => {
    expect(buildAuditQuery({
      page: 2,
      category: 'fees',
      actorEmail: ' admin@test.local ',
      action: '',
      keyword: ' payment ',
      from: '',
      to: '',
    })).toBe('page=2&page_size=20&category=fees&actor_email=admin%40test.local&q=payment');
  });

  it('converts local dates to inclusive ISO bounds', () => {
    const bounds = localDateBounds('2026-07-29', '2026-07-29');
    expect(new Date(bounds.from).getTime()).toBe(
      new Date(2026, 6, 29, 0, 0, 0, 0).getTime());
    expect(new Date(bounds.to).getTime()).toBe(
      new Date(2026, 6, 29, 23, 59, 59, 999).getTime());
  });
});
```

- [ ] **Step 2: Run query tests to verify RED**

Run:

```powershell
cd frontend
npm.cmd run test -- src/features/audit/auditQuery.test.js
```

Expected: module-not-found failure.

- [ ] **Step 3: Implement deterministic query construction**

```javascript
export const AUDIT_PAGE_SIZE = 20;

export function localDateBounds(from, to) {
  return {
    from: from ? new Date(`${from}T00:00:00`).toISOString() : '',
    to: to ? new Date(`${to}T23:59:59.999`).toISOString() : '',
  };
}

export function buildAuditQuery(filters) {
  const params = new URLSearchParams({
    page: String(filters.page || 1),
    page_size: String(AUDIT_PAGE_SIZE),
  });
  const dates = localDateBounds(filters.from, filters.to);
  const entries = {
    category: filters.category,
    actor_email: filters.actorEmail,
    action: filters.action,
    from: dates.from,
    to: dates.to,
    q: filters.keyword,
  };
  Object.entries(entries).forEach(([key, raw]) => {
    const value = typeof raw === 'string' ? raw.trim() : raw;
    if (value) params.set(key, value);
  });
  return params.toString();
}
```

- [ ] **Step 4: Run query tests to verify GREEN**

Run:

```powershell
npm.cmd run test -- src/features/audit/auditQuery.test.js
```

Expected: both query tests pass.

- [ ] **Step 5: Commit the frontend contract**

```powershell
git add frontend/src/features/audit
git commit -m "feat: build audit log queries"
```

## Task 4: Responsive Audit Page and States

**Files:**

- Create: `frontend/src/pages/audit/AuditLogsPage.jsx`
- Create: `frontend/src/pages/audit/AuditLogDetails.jsx`
- Create: `frontend/src/pages/audit/AuditLogsPage.test.jsx`
- Create: `frontend/src/pages/audit/audit-logs.css`

- [ ] **Step 1: Write failing page behavior tests**

Mock `api` and render with a memory router. Use these concrete fixtures:

```jsx
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

function renderAuditPage() {
  return render(
    <MemoryRouter>
      <AuditLogsPage />
    </MemoryRouter>,
  );
}
```

Cover one behavior per test:

```jsx
it('renders rows and opens the desktop details drawer', async () => {
  api.mockResolvedValueOnce(pageWithOneAudit());
  renderAuditPage();
  expect(await screen.findByText('Ghi nhận thanh toán')).toBeInTheDocument();
  await userEvent.click(screen.getAllByRole('button', { name: /Ghi nhận thanh toán/i })[0]);
  expect(screen.getByRole('dialog', { name: 'Chi tiết nhật ký' })).toBeInTheDocument();
  expect(screen.getByText('"amount": 500000')).toBeInTheDocument();
});

it('shows empty, retryable error, and permission states', async () => {
  api.mockResolvedValueOnce({ items: [], total: 0, page: 1, page_size: 20 });
  const view = renderAuditPage();
  expect(await screen.findByText('Không có nhật ký phù hợp')).toBeInTheDocument();
  view.unmount();

  api.mockRejectedValueOnce(new ApiError(500, 'Máy chủ bận'));
  renderAuditPage();
  expect(await screen.findByText('Máy chủ bận')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Thử lại' })).toBeInTheDocument();
});

it('debounces email and resets pagination', async () => {
  vi.useFakeTimers();
  api.mockResolvedValue({ items: [], total: 0, page: 1, page_size: 20 });
  renderAuditPage();
  await userEvent.type(screen.getByLabelText('Email người thao tác'), 'admin@');
  await vi.advanceTimersByTimeAsync(350);
  expect(api).toHaveBeenLastCalledWith(expect.stringContaining(
    'actor_email=admin%40'));
  vi.useRealTimers();
});
```

Add these focused cases to the same file:

```jsx
it('shows the dedicated permission state for HTTP 403', async () => {
  api.mockRejectedValueOnce(new ApiError(403, 'Cấm'));
  renderAuditPage();
  expect(await screen.findByText('Bạn không có quyền xem nhật ký'))
    .toBeInTheDocument();
});

it('expands a mobile audit card without mutation controls', async () => {
  api.mockResolvedValueOnce(pageWithOneAudit());
  renderAuditPage();
  const toggles = await screen.findAllByRole('button', {
    name: /Ghi nhận thanh toán/i,
  });
  const toggle = toggles[1];
  await userEvent.click(toggle);
  expect(toggle).toHaveAttribute('aria-expanded', 'true');
  expect(screen.queryByRole('button', { name: /sửa|xóa/i }))
    .not.toBeInTheDocument();
});

it('moves to the next page and closes the details drawer', async () => {
  api.mockResolvedValue(pageWithOneAudit({ total: 21 }));
  renderAuditPage();
  await screen.findByText('Ghi nhận thanh toán');
  await userEvent.click(screen.getByRole('button', { name: 'Sau →' }));
  expect(api).toHaveBeenLastCalledWith(expect.stringContaining('page=2'));
  await userEvent.click(screen.getAllByRole('button', {
    name: /Ghi nhận thanh toán/i,
  })[0]);
  await userEvent.click(screen.getByRole('button', { name: 'Đóng' }));
  expect(screen.queryByRole('dialog', { name: 'Chi tiết nhật ký' }))
    .not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run page tests to verify RED**

Run:

```powershell
npm.cmd run test -- src/pages/audit/AuditLogsPage.test.jsx
```

Expected: module-not-found failure.

- [ ] **Step 3: Implement data state and stale-response protection**

Use a request sequence matching the existing `UsersPage` pattern:

```jsx
const requestId = useRef(0);

const load = useCallback(async () => {
  const current = ++requestId.current;
  setLoading(true);
  setError(null);
  try {
    const data = await api(`/audit-logs?${buildAuditQuery({
      page, category, actorEmail: debouncedEmail, action,
      from, to, keyword: debouncedKeyword,
    })}`);
    if (current !== requestId.current) return;
    setItems(data.items);
    setTotal(data.total);
  } catch (caught) {
    if (current !== requestId.current) return;
    setError(caught);
  } finally {
    if (current === requestId.current) setLoading(false);
  }
}, [page, category, debouncedEmail, action, from, to, debouncedKeyword]);
```

Use a 350ms effect for email and keyword. Every immediate filter change calls
`setPage(1)`; debounced changes also reset page before loading.

- [ ] **Step 4: Implement read-only renderers**

`AuditLogDetails` must render JSON safely:

```jsx
export function Snapshot({ title, value }) {
  const text = value == null
    ? 'Không có dữ liệu'
    : JSON.stringify(value, null, 2);
  return (
    <section className="audit-snapshot">
      <h4>{title}</h4>
      <pre>{text}</pre>
    </section>
  );
}
```

Desktop rows use a button spanning the description cell so keyboard users can
open the drawer. Mobile cards use `aria-expanded` and render
`AuditLogDetails` inline. The error branch checks `error.status === 403` and
shows “Bạn không có quyền xem nhật ký”; other errors include “Thử lại”.

- [ ] **Step 5: Add responsive CSS**

Implement these layout boundaries:

```css
.audit-mobile-list { display: none; }
.audit-drawer-backdrop { position: fixed; inset: 0; z-index: 70; background: rgba(18,59,50,.42); }
.audit-drawer { position: fixed; z-index: 71; top: 0; right: 0; width: min(620px, 92vw); height: 100dvh; overflow: auto; background: var(--panel); box-shadow: var(--shadow-md); }
.audit-snapshot pre { overflow: auto; max-height: 320px; padding: 14px; border-radius: var(--radius-md); background: #17211e; color: #eef5f1; }

@media (max-width: 768px) {
  .audit-desktop-table { display: none; }
  .audit-mobile-list { display: grid; gap: 12px; }
  .audit-card { border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--panel); box-shadow: var(--shadow-sm); }
  .audit-card-toggle { width: 100%; min-height: 56px; padding: 14px; border: 0; background: transparent; text-align: left; }
  .audit-drawer, .audit-drawer-backdrop { display: none; }
}
```

At 769–1100px, set the drawer width to `min(560px, calc(100vw - 84px))`.

- [ ] **Step 6: Run page tests and full frontend suite**

Run:

```powershell
npm.cmd run test -- src/pages/audit/AuditLogsPage.test.jsx
npm.cmd run test
```

Expected: all audit page tests and all existing frontend tests pass.

- [ ] **Step 7: Commit the page**

```powershell
git add frontend/src/pages/audit
git commit -m "feat: add responsive audit log page"
```

## Task 5: Admin Route and Navigation

**Files:**

- Modify: `frontend/src/App.jsx`
- Modify: `frontend/src/components/Layout.jsx`
- Test: `frontend/src/components/Layout.audit.test.jsx`

- [ ] **Step 1: Write failing Admin navigation tests**

Use a mutable auth fixture and real nested routing:

```jsx
let currentUser;

vi.mock('../auth.jsx', () => ({
  ROLE_LABELS: { admin: 'Quản trị', teacher: 'GV chính', assistant: 'Trợ giảng' },
  useAuth: () => ({ user: currentUser, logout: vi.fn() }),
}));

function renderLayout(user) {
  currentUser = { id: 1, full_name: 'Test', ...user };
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<div>Tổng quan</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}
```

```jsx
it('shows audit navigation for admins', () => {
  renderLayout({ role: 'admin' });
  expect(screen.getByRole('link', { name: 'Nhật ký' })).toHaveAttribute(
    'href', '/logs');
});

it('hides audit navigation from teachers and assistants', () => {
  renderLayout({ role: 'teacher' });
  expect(screen.queryByRole('link', { name: 'Nhật ký' })).not.toBeInTheDocument();
});
```

Add this routing assertion with `currentUser` set to a teacher:

```jsx
it('redirects a teacher away from the audit route', async () => {
  currentUser = { id: 2, full_name: 'Teacher', role: 'teacher' };
  render(
    <MemoryRouter initialEntries={['/logs']}>
      <App />
    </MemoryRouter>,
  );
  await waitFor(() => {
    expect(screen.queryByText('Nhật ký hoạt động')).not.toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run route/navigation tests to verify RED**

Run:

```powershell
npm.cmd run test -- src/components/Layout.audit.test.jsx
```

Expected: “Nhật ký” link is absent for Admin.

- [ ] **Step 3: Add route and Admin-only links**

In `App.jsx`:

```jsx
import AuditLogsPage from './pages/audit/AuditLogsPage.jsx';

<Route
  path="logs"
  element={(
    <RequireAdmin>
      <AuditLogsPage />
    </RequireAdmin>
  )}
/>
```

Add an inline log/document SVG to `Layout.jsx`, then add this link in both
Admin-only navigation blocks:

```jsx
{user.role === 'admin' && (
  <NavLink to="/logs" onClick={closeSheet}>{I.logs}Nhật ký</NavLink>
)}
```

For the desktop link, omit `onClick`.

- [ ] **Step 4: Run route tests, full frontend tests, and production build**

Run:

```powershell
npm.cmd run test -- src/components/Layout.audit.test.jsx
npm.cmd run test
npm.cmd run build
```

Expected: all tests pass and Vite produces `dist/` without errors.

- [ ] **Step 5: Commit route integration**

```powershell
git add frontend/src/App.jsx frontend/src/components/Layout.jsx frontend/src/components/Layout.audit.test.jsx
git commit -m "feat: expose admin audit log navigation"
```

## Task 6: Regression, Local API, Migration, and Responsive QA

**Files:**

- Modify only files required by defects reproduced during this task.
- Update: `docs/superpowers/plans/2026-07-29-audit-log-browser.md` by checking completed steps.

- [x] **Step 1: Run full backend verification**

```powershell
cd java-backend
mvn.cmd test
mvn.cmd package
```

Expected: zero test failures and `target/class-management-backend-0.1.0.jar`
exists.

- [x] **Step 2: Run full frontend verification**

```powershell
cd ..\frontend
npm.cmd run test
npm.cmd run build
```

Expected: zero test failures and a successful production build in `dist/`.

- [x] **Step 3: Start the backend and smoke-test authorization/filtering**

Start the backend with the existing local SQL Server configuration, authenticate
as Admin, and call:

```powershell
Invoke-RestMethod 'http://127.0.0.1:4000/api/audit-logs?page=1&page_size=20&category=authentication&actor_email=admin'
```

Expected: HTTP 200 with `items`, `total`, `page`, and `page_size`.
Repeat with a teacher token and expect HTTP 403. Send POST to the same path and
expect HTTP 403/405 with no database mutation.

- [x] **Step 4: Verify migration compatibility**

Run Flyway validation through application startup against the existing local
SQL Server database. Confirm V1–V3 remain successful and that this feature adds
no migration because it only maps the existing nullable `user_id` relation.

- [x] **Step 5: Verify responsive layouts**

Run the frontend locally and inspect `/logs` at:

- 375px: cards expand inline; controls and pagination remain tappable.
- 768px: mobile-card layout remains active without horizontal overflow.
- 1366px: table and drawer fit beside the sidebar.
- 1920px: table uses available width without overlong snapshot lines.

At every width, verify loading, empty, error, permission, filtered-results,
drawer/card detail, keyboard focus, and no edit/delete controls.

- [x] **Step 6: Review the final diff**

Run:

```powershell
cd ..
git diff --check
git status --short
git log --oneline -8
```

Expected: no whitespace errors, no generated `dist` or `target` files staged,
and only intended source/test/doc changes.

- [x] **Step 7: Request code review and fix every Critical/Important finding**

Review the accumulated commits against
`docs/superpowers/specs/2026-07-29-audit-log-browser-design.md`. Re-run the
specific red test before each correction and the relevant green suite after it.

- [x] **Step 8: Create final local commit if QA produced corrections**

```powershell
git add --update java-backend/src frontend/src docs/superpowers/plans/2026-07-29-audit-log-browser.md
git commit -m "fix: harden audit log browser"
```

If QA produces no correction, do not create an empty commit. Do not push or
publish any branch.
