# UnitTest_Summary.md — RBL Week 4 Submission

## 1. Project Information

| Field | Value |
|-------|-------|
| Project | RMS Backend (Restaurant Management System) |
| Week 4 Focus | Unit testing + integration testing for the POS module |
| Selected Service | `OrderService` (`web.restaurant.swp.modules.pos.service`) |
| Selected Controller | `PosController` (`web.restaurant.swp.modules.pos.controller`) |

---

## 2. Tested Classes

| Test Class | Type | Framework | Test Cases |
|------------|------|-----------|------------|
| `OrderServiceTest` | Unit test | JUnit 5 + Mockito | 19 |
| `PosControllerIntegrationTest` | Integration test | `@SpringBootTest` + `MockMvc` | 10 |
| **Total** | | | **29** |

---

## 3. Test Execution Command

```powershell
.\mvnw.cmd "-Dtest=OrderServiceTest,PosControllerIntegrationTest" test
```

---

## 4. Test Result

| Metric | Value |
|--------|-------|
| Tests run | 29 |
| Passed | 29 |
| Failed | 0 |
| Errors | 0 |
| Build status | **SUCCESS** |

---

## 5. Coverage Summary (JaCoCo)

| Component | Instruction Coverage |
|-----------|---------------------|
| `OrderService` (tested service) | **93%** |
| `PosController` (tested controller) | **11%** |
| Overall project | **12%** |

---

## 6. Additional Analysis

### 6.1 Lowest Coverage Area

Within the Week 4 scope, `PosController` has the lowest coverage at 11%. This is because the 10 integration tests targeted representative endpoints (session open, cart add, kitchen send, active session, rooms, checkout) rather than exhaustively covering all 20+ endpoints. The untested portion includes bill split/merge HTTP endpoints, order log filtering, bank setting CRUD, room and table management, and branch admin user management.

### 6.2 Untested Methods / Features

The following `PosController` features remain uncovered or only partially covered:

- **Bill split and merge** — the `POST /api/pos/bill/split` and `POST /api/pos/bill/merge` endpoints are not tested at the integration level
- **Room and table management** — `POST /api/pos/rooms/add`, `POST /api/pos/tables/add`, `POST /api/pos/tables/update`, `POST /api/pos/tables/delete` are untested
- **Order log filtering** — `GET /api/pos/order-logs` with day/week/month range parameters is untested
- **Branch admin CRUD** — `POST /api/pos/branch-admins/add`, `POST /api/pos/branch-admins/update`, `POST /api/pos/branch-admins/delete` are untested

### 6.3 Proposed Additional Test Cases

| # | Test Case | Validates |
|---|-----------|-----------|
| 1 | `confirmPayment_ShouldReturnBadRequest_WhenSessionNotFound` | Error path when `POST /api/pos/checkout/confirm` receives a non-existent session ID |
| 2 | `splitBill_ShouldReturnTwoSessionIds_WhenValidDetailIds` | Correct parsing of comma-separated detail IDs and JSON response structure from `POST /api/pos/bill/split` |
| 3 | `deleteTable_ShouldReturnBadRequest_WhenTableIsOccupied` | Business rule preventing deletion of a table with `OCCUPIED` or `RESERVED` status |

### 6.4 Why Integration Tests Are Slower Than Unit Tests

| Test Class | Duration | Spring Context |
|------------|----------|----------------|
| `OrderServiceTest` | ~0.3s | No |
| `PosControllerIntegrationTest` | ~18s | Yes |

Unit tests run in under a second because they use Mockito to mock all dependencies and invoke `OrderService` methods directly — no framework initialization is needed.

Integration tests take approximately 60x longer because each run loads the full Spring Boot application context, including: bean scanning and wiring, H2 in-memory database initialization with Hibernate DDL, Spring Security filter chain setup, `DispatcherServlet` bootstrapping for MockMvc, and JSON serialization/deserialization for every HTTP request. Once the context is loaded, individual test requests execute quickly, but the initial context startup accounts for the majority of the runtime.

---

## 7. Scope Note

The Week 4 verification is based on the dedicated Week 4 test suite (`OrderServiceTest` and `PosControllerIntegrationTest`). The RMS Backend is a large multi-module system spanning Auth, Inventory, HR, Procurement, Loyalty, Promotion, Analytics, and KDS modules. Week 4 intentionally focused on one core service/controller pair — `OrderService` and `PosController` — and achieved **93% instruction coverage** on the selected service, demonstrating deep unit-level validation of the system's most critical business workflow.
