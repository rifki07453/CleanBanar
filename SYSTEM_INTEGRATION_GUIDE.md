# CleanBanar System Integration (IoT + Firebase + UI)

## 🎯 OBJECTIVE
Build a fully synchronized system between:
- IoT device (ESP32 / microcontroller)
- Firebase Realtime Database
- Android App UI (based on provided design)

The UI must strictly follow the provided design image.  
DO NOT redesign anything. Only implement logic and integration.

---

## ⚙️ 1. IOT (MICROCONTROLLER)

### Responsibilities:
- Read trash bin capacity using sensor (ultrasonic)
- Convert distance to percentage (0–100%)
- Send data to Firebase in real-time

### Data Rules:
- Send update only when value changes significantly (±3%)
- Include timestamp

### Example Payload:
```json
{
  "capacity": 85,
  "timestamp": 1710000000
}
```
**Endpoint:**
`bins/bin_1/`

---

## 🔥 2. FIREBASE STRUCTURE

Use Firebase Realtime Database.

**Main Nodes:**
```text
bins/
  bin_1:
    type: "organik"
    capacity: 85
    status: "hampir_penuh"
    lastUpdated: timestamp

notifications/
  notif_id:
    type: "penuh"
    bin_id: "bin_1"
    timestamp: number

history/
  history_id:
    type: "penuh" | "dikosongkan"
    bin_type: "organik"
    capacity: number
    petugas: string
    timestamp: number

users/
  user_id:
    role: "admin" | "petugas"
    notification_settings:
      hampir_penuh: true
      penuh: true
      selesai: true
```

---

## 🧠 3. STATUS LOGIC (CRITICAL)
- **0–79%**   → Normal
- **80–94%**  → Hampir Penuh
- **95–100%** → Penuh

**Rules:**
- Only update status if it CHANGES
- Store last status to prevent duplicate triggers

---

## 🔔 4. NOTIFICATION SYSTEM

**Trigger Conditions:**
- Normal → Hampir Penuh
- Hampir Penuh → Penuh

**Rules:**
- Do NOT send duplicate notifications
- Check user `notification_settings` before showing

**Optional Safety:**
- "Penuh" can be forced always ON

---

## 📱 5. ANDROID APP (UI INTEGRATION)

**RULE:**
UI must EXACTLY match the provided design image.

**🔄 Real-Time Binding**
Listen to:
- `bins/`
- `notifications/`
- `history/`

**Update:**
- Dashboard (capacity + status)
- History timeline
- Notification list

---

## 📊 6. HISTORY (TIMELINE UI)

**Behavior:**
- Sort newest first
- Group by:
  - Hari ini
  - Kemarin

**Item Content:**
- Status badge (warna sesuai status)
- Title (Organik / Non-Organik)
- Description
- Timestamp (kanan)

---

## 🔘 7. USER ACTION

**Button:**
"Tandai Telah Dikosongkan"

**Action:**
- Update Firebase:
  - `capacity = 0`
  - `status = Normal`
- Add history entry
- Trigger notification:
  - "Selesai dikosongkan"

---

## 🔧 8. NOTIFICATION SETTINGS (PROFILE)

**UI:**
Toggle switches:
- Hampir Penuh
- Penuh
- Selesai

**Behavior:**
- Save to Firebase
- Apply instantly (real-time)

---

## 🧪 9. EDGE CASE HANDLING

- **No history:** → "Belum ada riwayat aktivitas"
- **No notifications:** → "Tidak ada notifikasi"
- **No data:** → show loading state
- **Sensor error:** → ignore invalid values
- **Offline:** → show last known data

---

## 🚫 CONSTRAINTS

- Do NOT redesign UI
- Do NOT add unnecessary animations
- Do NOT overcomplicate charts
- Keep everything lightweight and real-time

---

## 🎯 FINAL EXPECTATION

**Deliver:**
- IoT → Firebase connection flow
- Firebase → App real-time listener
- Clean UI binding (no layout changes)
- Stable notification logic (no spam)
