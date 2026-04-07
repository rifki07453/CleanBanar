# CleanBanar

**CleanBanar** is a Smart Trash monitoring system designed for modern waste management. It provides a real-time, interactive dashboard for both Admins and Staff to efficiently monitor trash levels, receive critical notifications, and manage personnel.

## Features
- **Monitoring real-time**: Keep track of trash bin capacities constantly.
- **Notifikasi penuh**: Instant alerts when a bin reaches maximum capacity.
- **Statistik**: Visual charts detailing waste accumulation and trends.
- **Manajemen petugas**: Administrative control to manage staff and assignments.

## Tech Stack
- Frontend: Android App (Kotlin / XML / ViewBinding)
- Backend & Database: Firebase (Realtime Database / BoM)

## How to Run the Project
1. Clone the repository:
   ```bash
   git clone <repository_url>
   ```
2. Open the project in Android Studio.
3. Sync the Gradle files to download the necessary dependencies.
4. Setup Firebase:
   - Make sure your `google-services.json` is placed in the `app/` directory.
5. Build and run the project on an Android emulator or a physical device.

---

# UI/UX Evaluation & Improvement Guide

Based on the provided design reference for the CleanBanar mobile application, here are the targeted improvements for usability, clarity, and interactivity. The primary layout, structure, and color system are strictly maintained.

## 1. UI Improvement Suggestions
- **Action Feedback**: Add a subtle loading spinner to action buttons (e.g., "Tandai Telah Dikosongkan"). Once completed, temporarily replace the button text with success ("Berhasil dikosongkan") or error ("Gagal, coba lagi") feedback.
- **Charts (Statistik)**: Preserve the current chart structure but increase chart line thickness for better visibility. Ensure that axis labels use a higher contrast gray. Keep organic and non-organic chart lines strictly green and blue respectively to match the card colors above them.
- **Profile Page Details**: Add an explicit role label ("Admin" or "Petugas") below the name and a circular avatar placeholder to ground the user's identity. Optionally, display an "Aktivitas Terakhir" (Last login) timestamp.
- **Dashboard Consistency**: Integrate a subtle real-time update indicator on the dashboard (e.g., a pulsing green dot) and explicitly show "Online" or "Offline" status to confirm system connectivity. 

## 2. UX Improvements
- **Button Disabling**: Prevent overlapping tasks or duplicate entries by disabling primary action buttons instantly after they are clicked, until the network responds.
- **History Status Indicators**: Ensure history items explicitly show "Penuh" vs "Dikosongkan" using color-coded chips (e.g., red for full, green for emptied) to make scanning past events instantaneous.
- **Notification Hierarchy**: Improve notification card layouts by clearly segregating the title (bold), description (regular), and time (light gray, smaller). Use the left-side border indicators systematically (Red = Penuh, Yellow = Hampir Penuh, Green = Selesai).

## 3. Missing States (System States)
Add the following system states to prevent user confusion when loading or failing to fetch data, using standard Indonesian copy:
- **Loading State**: Implement skeleton views for the dashboard cards and statistics while data is being fetched.
- **Empty State**: 
  - Notifications: "Belum ada notifikasi"
  - History: "Belum ada riwayat"
  - General Data: "Data masih kosong"
- **Error State**: "Gagal memuat data" or "Periksa koneksi Anda" on failed Firebase queries.

## 4. Interaction Improvements
- **Bottom Navigation State**: Ensure the active tab remains brightly highlighted in the brand's primary green, while inactive tabs remain muted gray.
  - *Admin Navigation*: Dashboard, Manajemen Petugas, Statistik, Notifikasi, Profil.
  - *Staff Navigation*: Home, Statistik, History, Notifikasi, Profil.

## 5. Minor Visual Fixes
- **Spacing Guidelines**: Increase vertical padding slightly between notification cards to prevent clutter.
- **Alignment**: Ensure that all percentages and subtext on the Dashboard and Statistics pages perfectly align to the left margins of their respective container cards.
- **Typography Consistency**: Eliminate conflicting font weights. Use bold strictly for titles and medium/regular for subtitles and descriptions.
