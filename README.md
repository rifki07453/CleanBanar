# CleanBanar

**CleanBanar** is a Smart Trash Bin monitoring system built for modern campus waste management. It provides real-time, interactive dashboards for both **Admin** and **Petugas (Staff)** to efficiently monitor trash bin capacity, receive critical notifications, and manage personnel.

## Features

- **Monitoring Real-Time**: Live tracking of Organik & Non-Organik bin capacity via IoT sensors
- **Notifikasi Otomatis**: Automatic alerts when bins reach ≥80% (Hampir Penuh) and ≥95% (Penuh)
- **Statistik**: Visual bar charts showing 7-day capacity trends, weekly averages, and total penuh events
- **Manajemen Petugas**: Admin-only staff management panel (add, edit, remove petugas)
- **Riwayat Aktivitas**: Full activity log of bin alerts and emptying events

## Tech Stack

| Layer           | Technology                        |
|-----------------|-----------------------------------|
| **Frontend**    | Android (Kotlin, XML, ViewBinding)|
| **Database**    | Firebase Realtime Database        |
| **IoT Device**  | ESP32 (sends raw sensor data)     |
| **Architecture**| Hybrid — Firebase as logic bridge |

## System Architecture

```
ESP32 Sensor → Firebase Realtime DB → Android App (Admin / Petugas)
     ↑                ↑                       ↓
 Raw data only   Source of truth      Display + User actions
```

**Responsibility Separation:**
- **ESP32**: Sends raw sensor data (distance/capacity). No decision logic.
- **Firebase**: Stores raw data, processed states (status, notifications, history).
- **Android App**: Handles UI display and user-triggered actions (mark as emptied). Lightweight observers for threshold notifications.

## Firebase Database Structure

```
cleanbanar/
  bins/
    organik/       { percentage: Int, status: String, lastUpdate: Long }
    nonOrganik/    { percentage: Int, status: String, lastUpdate: Long }
  device/
    connectionStatus: String ("ONLINE" / "OFFLINE")
    lastSeen: Long
  notifications/
    {id}/          { title, message, type, timestamp, read }
  history/
    {id}/          { action, bin, actor, timestamp }
  users/
    {id}/          { name, email, role }
  statistics/
    daily/
      {date}/      { organik: Int, nonOrganik: Int }
```

## Key System Logic

### Notification Triggers (BinObserver)
- **≥80%**: "Hampir Penuh" warning notification
- **≥95%**: "Penuh" danger notification + history alert entry
- Duplicate prevention via last-state comparison
- Anomalous data (outside 0-100%) is ignored

### Data Consistency ("Tandai Telah Dikosongkan")
When a Petugas marks a bin as emptied, `FirebaseManager.emptyBin()` atomically:
1. Resets capacity to 0% and status to "TERSEDIA"
2. Writes a history entry ("emptied")
3. Sends a "dikosongkan" notification
4. Updates daily statistics

### Edge Cases
- **Device Offline**: Shows last known data + "Terputus" status
- **Duplicate Actions**: Buttons are disabled during processing
- **Sensor Anomaly**: Values outside 0-100% are clamped or ignored

## How to Run

1. Clone the repository:
   ```bash
   git clone https://github.com/rifki07453/CleanBanar.git
   ```
2. Open the project in **Android Studio**.
3. Sync the Gradle files to download dependencies.
4. Setup Firebase:
   - Place your `google-services.json` in the `app/` directory.
   - Ensure Firebase Realtime Database rules allow read/write.
5. Build and run on an Android emulator or physical device.

## Bottom Navigation

| Admin                  | Petugas (Staff)       |
|------------------------|-----------------------|
| Dashboard              | Home                  |
| Manajemen Petugas      | Statistik             |
| Statistik              | History               |
| Notifikasi             | Notifikasi            |
| Profil                 | Profil                |
