# GUIDE.md — RMS Backend Week 4 Testing Guide

## 1. How to Run Tests

### Week 4 Verification Command (Official)

This is the command used to verify the Week 4 deliverables. It executes only the two test classes created for this assignment:

```powershell
.\mvnw.cmd "-Dtest=OrderServiceTest,PosControllerIntegrationTest" test
```

This runs:
- `OrderServiceTest` — 19 unit tests for `OrderService`
- `PosControllerIntegrationTest` — 10 integration tests for `PosController`

The `test` profile is activated automatically via the Maven profile configured in `pom.xml`, which ensures:
- H2 in-memory database is used instead of PostgreSQL
- `DataSeeder` is skipped
- Redis and real mail services are bypassed

### Full Project Test Command

The standard full-suite command also works:

```powershell
.\mvnw.cmd test
```

However, note that the full project suite includes legacy tests (e.g., `LiteFlowServiceTests`, `SwpApplicationTests`) that are outside the Week 4 scope and depend on pre-seeded PostgreSQL data. **The official Week 4 result is based on the scoped command above, not the full project suite.**

---

## 2. How to View the JaCoCo Report

After running tests, the JaCoCo HTML coverage report is generated at:

```
target/site/jacoco/index.html
```

Open this file in a browser to view:
- **Instruction coverage** per package and class
- **Branch coverage** per package and class
- **Line-by-line** coverage details
- **Method-level** and **class-level** breakdowns

---

## 3. Test Files Created for Week 4

### Unit Test — `OrderServiceTest`

**File:** `src/test/java/web/restaurant/swp/modules/pos/service/OrderServiceTest.java`

| Attribute | Value |
|-----------|-------|
| Tests | 19 |
| Type | Unit (Mockito, no Spring context) |
| Service | `OrderService` |

**What it tests:**

| Method | Scenarios |
|--------|-----------|
| `openTableSession` | Table is empty (success), table not found, table is occupied |
| `addItemToSession` | Valid input, session not found, variant not found, multi-item total calculation |
| `sendToKitchen` | Pending order sent, non-pending order skipped |
| `splitBill` | Session not found, successful split creating new session |
| `mergeBill` | Orders moved to target, source table released |
| `generateVNPayQR` | Basic QR generation, Platinum tier discount |
| `confirmPayment` | Happy path with loyalty, bank transfer, no-customer case |
| `getTablesByBranch` | Returns correct table list |

### Integration Test — `PosControllerIntegrationTest`

**File:** `src/test/java/web/restaurant/swp/modules/pos/controller/PosControllerIntegrationTest.java`

| Attribute | Value |
|-----------|-------|
| Tests | 10 |
| Type | Integration (`@SpringBootTest` + `MockMvc`) |
| Controller | `PosController` |

**What it tests:**

| Endpoint | Scenarios |
|----------|-----------|
| `POST /api/pos/session/open` | Valid table (200), table not found (400) |
| `POST /api/pos/order/add` | Add item to cart (200) |
| `POST /api/pos/order/send` | Session not found (400) |
| `GET /api/pos/session/active` | Session found (200), no session (404) |
| `GET /api/pos/rooms` | Authenticated access (200), empty result (200) |
| `POST /api/pos/rooms/add` | Cashier forbidden (403) |
| `POST /api/pos/checkout/vnpay` | QR data returned (200) |
| `POST /api/pos/checkout/confirm` | Payment processed (200) |

---

## 4. Why These Were Selected

### OrderService

- Core POS business workflow: session management, cart, billing, payment, loyalty integration
- 8 public methods with clear, testable business rules
- 7 mockable dependencies (5 repositories + 2 services)
- Stable enough for thorough unit-level validation

### PosController

- HTTP-facing counterpart of OrderService with 15+ REST endpoints
- Tests real HTTP request/response behavior through MockMvc
- Validates Spring Security role enforcement (CASHIER vs. ADMIN)
- Covers the full MVC pipeline: request parsing, controller routing, JSON serialization

---

## 5. Test Configuration Changes

### `pom.xml`

- Added `jacoco-maven-plugin` (v0.8.12) with `prepare-agent` and `report` goals
- Added `<profile>` section to activate the `test` profile via Maven

### `src/test/resources/application-test.properties` (new)

- H2 in-memory datasource (`jdbc:h2:mem:testdb`)
- Redis auto-configuration excluded
- Mail configured to localhost (no real SMTP)
- Dummy Google OAuth2 credentials

### `DataSeeder.java` (modified)

- Injected `ConfigurableEnvironment`
- Added test profile check: skips database seeding when `test` profile is active

### JaCoCo Report

- Generates HTML coverage report at `target/site/jacoco/index.html`
- Runs automatically during the Maven `test` phase

---

## 6. Week 4 Deliverables

| Deliverable | File | Description |
|-------------|------|-------------|
| Unit test summary | `UnitTest_Summary.md` | Concise summary of tested classes, test case counts, and pass/fail results |
| Coverage report | `CoverageReport.html` | Self-contained JaCoCo HTML report with inline CSS — opens directly in any browser |
| Detailed report | `WEEK4_RESULT.md` | Full structured result report with coverage interpretation and analysis |
| Testing guide | `GUIDE.md` | This file — how to run tests and understand the test suite |
