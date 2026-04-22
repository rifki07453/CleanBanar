# CleanBanar

**CleanBanar** adalah sistem pemantauan Tempat Sampah Pintar (Smart Trash Bin) yang dibangun untuk pengelolaan sampah kampus modern. Sistem ini menyediakan dasbor interaktif secara *real-time* baik untuk **Admin** maupun **Petugas** agar dapat memantau kapasitas tempat sampah secara efisien, menerima notifikasi penting, dan mengelola personil.

## Fitur Utama

- **Pemantauan Real-Time**: Pelacakan langsung kapasitas tempat sampah Organik & Non-Organik melalui sensor IoT.
- **Notifikasi Otomatis**: Peringatan otomatis saat tempat sampah mencapai ≥80% (Hampir Penuh) dan ≥95% (Penuh), dengan tampilan UI kartu yang indah.
- **Statistik**: Diagram batang visual yang menunjukkan tren kapasitas 7 hari terakhir, rata-rata mingguan, dan total kejadian penuh.
- **Manajemen Petugas**: Panel admin terpusat (dapat diakses langsung dari Dasbor Admin) untuk menambah, melihat, dan menghapus petugas.
- **Riwayat Aktivitas**: Linimasa aktivitas universal (tersedia untuk Admin dan Petugas) yang mencatat peringatan status dan riwayat pengosongan tempat sampah.
- **Keamanan Akun**: Antarmuka masuk (Login) yang aman dengan fitur UX modern (misalnya, ikon lihat/sembunyikan kata sandi).

## Teknologi Utama (Tech Stack)

| Lapisan           | Teknologi                         |
|-------------------|-----------------------------------|
| **Frontend**      | Android (Kotlin, XML, ViewBinding)|
| **Database**      | Firebase Realtime Database        |
| **Perangkat IoT** | ESP32 (mengirim data sensor mentah)|
| **Arsitektur**    | Hybrid — Firebase sebagai logika pusat |

## Arsitektur Sistem

```text
Sensor ESP32 → Firebase Realtime DB → Aplikasi Android (Admin / Petugas)
     ↑                ↑                       ↓
 Hanya data mentah  Sumber data utama    Tampilan UI + Aksi Pengguna
```

**Pemisahan Tanggung Jawab:**
- **ESP32**: Hanya mengirim data jarak/kapasitas mentah dari sensor. Tidak ada logika keputusan.
- **Firebase**: Menyimpan data mentah dan status yang telah diproses (status, notifikasi, riwayat).
- **Aplikasi Android**: Menangani tampilan UI dan aksi yang dipicu pengguna (menandai "Telah Dikosongkan"). Memiliki pemantau ringan untuk memicu notifikasi berbasis ambang batas (threshold).

## Struktur Database Firebase

```text
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

## Logika Sistem Inti

### Pemicu Notifikasi (BinObserver)
- **≥80%**: Peringatan "Hampir Penuh" (Warning)
- **≥95%**: Peringatan "Penuh" (Bahaya) + pencatatan riwayat
- Pencegahan duplikasi melalui pemeriksaan status terakhir (last-state comparison).
- Data anomali (di luar rentang 0-100%) akan diabaikan.

### Konsistensi Data ("Tandai Telah Dikosongkan")
Saat Petugas menandai tempat sampah telah dikosongkan, `FirebaseManager.emptyBin()` secara otomatis akan:
1. Mereset kapasitas menjadi 0% dan status menjadi "TERSEDIA".
2. Menulis catatan riwayat ("dikosongkan").
3. Mengirimkan notifikasi "dikosongkan" ke sistem.
4. Memperbarui data statistik harian.

### Penanganan Kondisi Khusus (Edge Cases)
- **Perangkat Luring (Offline)**: Menampilkan data pemantauan terakhir + status "Terputus".
- **Aksi Ganda**: Tombol sengaja dinonaktifkan sementara (disabled) selama proses komunikasi ke database untuk mencegah klik ganda.
- **Anomali Sensor**: Nilai sensor di luar kewajaran 0-100% akan dibatasi (clamped) atau diabaikan sepenuhnya.

## Cara Menjalankan Aplikasi

1. *Clone* repositori ini:
   ```bash
   git clone https://github.com/rifki07453/CleanBanar.git
   ```
2. Buka proyek ini di dalam **Android Studio**.
3. Lakukan *Sync* Gradle untuk mengunduh dependensi (Proyek ini dikonfigurasi menggunakan JDK 21).
4. Pengaturan Firebase:
   - Letakkan file asli `google-services.json` milik Anda di dalam direktori `app/`.
   - Pastikan aturan Firebase Realtime Database Anda diatur untuk mengizinkan instruksi baca/tulis.
5. Jalankan (*Run*) kode di emulator Android atau perangkat fisik langsung.

## Tata Letak Navigasi

| Peran (Role) Admin     | Peran (Role) Petugas  |
|------------------------|-----------------------|
| Dashboard              | Home                  |
| Statistics             | Statistik             |
| History                | History               |
| Notifications          | Notifikasi            |
| Profile                | Profil                |

*Catatan: Fungsionalitas Manajemen Petugas khusus Admin kini telah terpadu sempurna ke dalam Dasbor utama sebagai kartu akses cepat, memberikan ruang yang lebih leluasa pada bilah navigasi bawah (bottom navigation).*
