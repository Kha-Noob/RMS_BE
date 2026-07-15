# RMS Backend — Verified API Test Commands

> All commands tested and verified against running backend.
> Generated: 2026-07-03

---

## Connection Info

| Item | Value |
|------|-------|
| Base URL | `http://localhost:8080` |
| Auth | HTTP Basic Auth |
| Admin Email | `admin@liteflow.com` |
| Admin Password | `Admin123!` |
| Branch ID | `01-2thang9` |
| Admin Role | ADMIN |
| Database | PostgreSQL 16.14, port 5433, database `swp` |

---

## Base Variables

### PowerShell
```powershell
$bytes = [System.Text.Encoding]::UTF8.GetBytes("admin@liteflow.com:Admin123!")
$b64 = [System.Convert]::ToBase64String($bytes)
$BASE = "http://localhost:8080"
$H = @{Authorization = "Basic $b64"}
$HJ = @{Authorization = "Basic $b64"; "Content-Type" = "application/json"}
```

### Git Bash / Linux
```bash
BASE="http://localhost:8080"
AUTH=$(echo -n 'admin@liteflow.com:Admin123!' | base64)
```

---

## 1. AUTH

**GET /api/auth/me** — Get current user
```powershell
Invoke-RestMethod -Method GET -Uri "$BASE/api/auth/me" -Headers $H
```
```bash
curl -s -X GET "$BASE/api/auth/me" -H "Authorization: Basic $AUTH"
```
Expected: `{"id":1,"email":"admin@liteflow.com","name":"Chủ chuỗi Admin","roles":["ADMIN"],...}`

---

## 2. MENU (11 endpoints) — ALL VERIFIED ✅

**GET /api/menu** — List all menu items
```powershell
Invoke-RestMethod -Method GET -Uri "$BASE/api/menu" -Headers $H
```
```bash
curl -s -X GET "$BASE/api/menu" -H "Authorization: Basic $AUTH"
```
Expected: 20 items, each with `id, name, priceVnd, status, category, variants`

**GET /api/menu/active** — List active items (for POS)
```powershell
Invoke-RestMethod -Method GET -Uri "$BASE/api/menu/active" -Headers $H
```
```bash
curl -s -X GET "$BASE/api/menu/active" -H "Authorization: Basic $AUTH"
```
Expected: 20 active items

**GET /api/menu/{id}** — Get single item
```powershell
Invoke-RestMethod -Method GET -Uri "$BASE/api/menu/1" -Headers $H
```
```bash
curl -s -X GET "$BASE/api/menu/1" -H "Authorization: Basic $AUTH"
```
Expected: `{"id":1,"name":"Phở Bò","priceVnd":85000,"variants":[...],...}`

**POST /api/menu** — Create menu item
```powershell
$body = '{"name":"Bún Đậu Mắm Tôm","description":"Bún đậu mắm tôm","priceVnd":65000,"categoryId":2,"status":"ACTIVE","variants":[{"name":"Phần nhỏ","priceVnd":55000},{"name":"Phần lớn","priceVnd":75000}]}'
Invoke-RestMethod -Method POST -Uri "$BASE/api/menu" -Headers $HJ -Body $body
```
```bash
curl -s -X POST "$BASE/api/menu" \
  -H "Authorization: Basic $AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Bún Đậu Mắm Tôm","description":"Bún đậu mắm tôm","priceVnd":65000,"categoryId":2,"status":"ACTIVE","variants":[{"name":"Phần nhỏ","priceVnd":55000},{"name":"Phần lớn","priceVnd":75000}]}'
```
Expected: `{"id":21,"name":"Bún Đậu Mắm Tôm",...}`

**PUT /api/menu/{id}** — Update menu item
```powershell
$body = '{"name":"Phở Bò Đặc Biệt","priceVnd":95000,"categoryId":1,"status":"ACTIVE","variants":[{"name":"Phở tái","priceVnd":95000}]}'
Invoke-RestMethod -Method PUT -Uri "$BASE/api/menu/1" -Headers $HJ -Body $body
```
```bash
curl -s -X PUT "$BASE/api/menu/1" \
  -H "Authorization: Basic $AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Phở Bò Đặc Biệt","priceVnd":95000,"categoryId":1,"status":"ACTIVE","variants":[{"name":"Phở tái","priceVnd":95000}]}'
```
Expected: `{"id":1,"name":"Phở Bò Đặc Biệt","priceVnd":95000,...}`

**PATCH /api/menu/{id}/status** — Toggle status
```powershell
Invoke-RestMethod -Method PATCH -Uri "$BASE/api/menu/1/status" -Headers $HJ -Body '{"status":"INACTIVE"}'
```
```bash
curl -s -X PATCH "$BASE/api/menu/1/status" \
  -H "Authorization: Basic $AUTH" -H "Content-Type: application/json" \
  -d '{"status":"INACTIVE"}'
```
Expected: `{"id":1,...,"status":"INACTIVE",...}`

**DELETE /api/menu/{id}** — Delete item
```powershell
Invoke-RestMethod -Method DELETE -Uri "$BASE/api/menu/21" -Headers $H
```
```bash
curl -s -X DELETE "$BASE/api/menu/21" -H "Authorization: Basic $AUTH"
```
Expected: `{"message":"Đã xóa món ăn thành công"}`

**GET /api/menu/categories** — List categories
```powershell
Invoke-RestMethod -Method GET -Uri "$BASE/api/menu/categories" -Headers $H
```
```bash
curl -s -X GET "$BASE/api/menu/categories" -H "Authorization: Basic $AUTH"
```
Expected: 4 categories: Món chính, Món phụ, Đồ uống, Tráng miệng

**POST /api/menu/categories** — Create category
```powershell
Invoke-RestMethod -Method POST -Uri "$BASE/api/menu/categories" -Headers $HJ -Body '{"name":"Ăn vặt","description":"Món ăn vặt","displayOrder":5,"active":true}'
```
```bash
curl -s -X POST "$BASE/api/menu/categories" \
  -H "Authorization: Basic $AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Ăn vặt","description":"Món ăn vặt","displayOrder":5,"active":true}'
```
Expected: `{"id":5,"name":"Ăn vặt",...}`

**PUT /api/menu/categories/{id}** — Update category
```powershell
Invoke-RestMethod -Method PUT -Uri "$BASE/api/menu/categories/5" -Headers $HJ -Body '{"name":"Ăn vặt cao cấp"}'
```
```bash
curl -s -X PUT "$BASE/api/menu/categories/5" \
  -H "Authorization: Basic $AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Ăn vặt cao cấp"}'
```
Expected: `{"id":5,"name":"Ăn vặt cao cấp",...}`

**DELETE /api/menu/categories/{id}** — Delete category
```powershell
Invoke-RestMethod -Method DELETE -Uri "$BASE/api/menu/categories/5" -Headers $H
```
```bash
curl -s -X DELETE "$BASE/api/menu/categories/5" -H "Authorization: Basic $AUTH"
```
Expected: `{"message":"Đã xóa danh mục thành công"}`

---

## Verified Results

| Endpoint | Method | Status | Verified |
|----------|--------|--------|:--------:|
| `/api/auth/me` | GET | 200 | ✅ |
| `/api/menu` | GET | 200 | ✅ |
| `/api/menu/active` | GET | 200 | ✅ |
| `/api/menu/{id}` | GET | 200 | ✅ |
| `/api/menu` | POST | 200 | ✅ |
| `/api/menu/{id}` | PUT | 200 | ✅ |
| `/api/menu/{id}/status` | PATCH | 200 | ✅ |
| `/api/menu/{id}` | DELETE | 200 | ✅ |
| `/api/menu/categories` | GET | 200 | ✅ |
| `/api/menu/categories` | POST | 200 | ✅ |
| `/api/menu/categories/{id}` | PUT | 200 | ✅ |
| `/api/menu/categories/{id}` | DELETE | 200 | ✅ |

**All 11 Menu endpoints: VERIFIED ✅**
