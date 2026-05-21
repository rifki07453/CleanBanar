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

| Lapisan           | Teknologi                              |
| ----------------- | -------------------------------------- |
| **Frontend**      | Android (Kotlin, XML, ViewBinding)     |
| **Database**      | Firebase Realtime Database             |
| **Perangkat IoT** | ESP32 (mengirim data sensor mentah)    |
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

## Fitur Keamanan Aplikasi

Aplikasi **CleanBanar** dirancang dengan mengutamakan standar keamanan aplikasi seluler modern guna melindungi data pengguna, kredensial masuk, dan integritas komunikasi data. Berikut adalah aspek keamanan utama yang diterapkan:

1. **Autentikasi Aman & Re-autentikasi (Firebase Auth)**
   - Proses masuk akun dikelola secara aman menggunakan **Firebase Authentication** dengan enkripsi standar industri.
   - Fitur **Re-autentikasi** diterapkan saat pengguna ingin mengubah kata sandi pada menu profil, di mana sistem mewajibkan pengguna untuk memasukkan kata sandi lama mereka guna memvalidasi kepemilikan akun sebelum memperbarui kata sandi baru.

2. **Enkripsi Sesi Lokal (EncryptedSharedPreferences)**
   - Data kredensial lokal dan informasi sesi (seperti UID, nama, email, dan peran pengguna) disimpan menggunakan **EncryptedSharedPreferences** yang dienkripsi menggunakan algoritma **AES-256 (GCM & SIV)** berbasis master key tingkat sistem.
   - Ini memastikan data sesi tidak dapat dibaca oleh aplikasi pihak ketiga atau diekstraksi secara langsung meskipun aplikasi dijalankan pada perangkat Android yang telah di-*root*.

3. **Pencegahan Perekaman Layar (Screen Capture & Recording Prevention)**
   - Halaman autentikasi utama (`LoginActivity`) dilindungi dengan flag `FLAG_SECURE`.
   - Hal ini mencegah sistem Android melakukan tangkapan layar (*screenshot*) atau merekam layar (*screen recording*) pada halaman sensitif tersebut, guna menghindari pencurian kredensial masuk.

4. **Konfigurasi Keamanan Jaringan (Network Security Config)**
   - **Enforce HTTPS**: Melalui berkas `network_security_config.xml`, aplikasi memblokir seluruh lalu lintas data dalam bentuk teks polos (HTTP) dengan parameter `cleartextTrafficPermitted="false"`. Seluruh komunikasi data dengan Firebase diwajibkan menggunakan HTTPS (SSL/TLS).
   - **Hanya Memercayai CA Sistem**: Aplikasi dikonfigurasi untuk hanya memercayai otoritas sertifikat (CA) resmi dari sistem Android (`<certificates src="system"/>`) dan mengabaikan sertifikat buatan pengguna (*user-installed certificates*). Hal ini melindungi komunikasi data dari serangan *Man-in-the-Middle* (MitM).

5. **Pencegahan Eksfiltrasi Data melalui Cadangan (Disabled App Backup)**
   - Fitur pencadangan otomatis dinonaktifkan di `AndroidManifest.xml` dengan menyetel `android:allowBackup="false"` dan `android:fullBackupContent="false"`.
   - Pencegahan ini memastikan data sensitif di dalam aplikasi tidak dapat diekstraksi ke luar perangkat menggunakan fitur cadangan ADB (`adb backup`).

6. **Kontrol Akses Berbasis Peran (Role-Based Access Control - RBAC)**
   - Pembatasan akses menu dan fitur dilakukan secara ketat di tingkat aplikasi dan basis data Firebase berdasarkan peran pengguna (`Admin` atau `Petugas`).
   - Fitur administratif sensitif seperti manajemen personil/petugas hanya dapat diakses oleh akun dengan peran terverifikasi sebagai `Admin`.

## Panduan Pengujian Fitur Keamanan

Untuk memverifikasi bahwa fitur keamanan di atas berfungsi dengan baik, Anda dapat melakukan langkah-langkah pengujian berikut:

### 1. Pengujian Autentikasi & Re-autentikasi (Firebase Auth)
* **Uji Login Salah**: Masuk ke halaman Login, masukkan email terdaftar tetapi gunakan password yang salah. Sistem harus menolak dan menampilkan pesan *"Password salah"*.
* **Uji Re-autentikasi Ubah Password**:
  1. Masuk (*login*) ke aplikasi.
  2. Buka menu **Profil** -> Klik **Ubah Password**.
  3. Masukkan password lama yang salah, lalu isi password baru. Klik **Simpan**.
  4. **Hasil yang diharapkan**: Sistem menampilkan pesan *"Password lama salah. Coba lagi."* dan menolak perubahan.
  5. Ulangi dengan password lama yang benar. Password harus berhasil diperbarui.

### 2. Pengujian Enkripsi Sesi Lokal (EncryptedSharedPreferences)
* **Uji Enkripsi Berkas XML**:
  1. Jalankan aplikasi pada Emulator Android Studio (rekomendasi menggunakan emulator dengan akses root/Google APIs).
  2. Masuk ke aplikasi agar data sesi tersimpan.
  3. Di Android Studio, buka tab **Device File Explorer** (biasanya di sisi kanan bawah).
  4. Telusuri folder berikut: `/data/data/com.example.cleanbanar/shared_prefs/`.
  5. Buka berkas `clean_banar_auth.xml`.
  6. **Hasil yang diharapkan**: Seluruh nama kunci (*key*) dan nilai (*value*) di dalam berkas XML tersebut berupa teks acak terenkripsi (seperti strings Base64/sandi acak) dan tidak dapat dibaca secara langsung (tidak ada teks polos seperti `"Admin"`, `"petugas@cleanbanar.com"`, atau ID pengguna).

### 3. Pengujian Pencegahan Tangkapan Layar (FLAG_SECURE)
* **Uji Tangkapan Layar (Screenshot)**:
  1. Buka halaman **Login** di aplikasi.
  2. Coba lakukan tangkapan layar menggunakan tombol fisik perangkat (`Power + Volume Down`) atau melalui fitur screenshot bawaan emulator.
  3. **Hasil yang diharapkan**: Sistem Android akan menolak aksi tersebut dan menampilkan notifikasi *"Tidak dapat mengambil tangkapan layar karena kebijakan keamanan"* (atau layar hasil tangkapan akan berwarna hitam pekat).
* **Uji Perekaman Layar (Screen Recording)**:
  1. Mulai perekaman layar di perangkat/emulator Anda.
  2. Buka halaman **Login** aplikasi CleanBanar.
  3. Hentikan perekaman dan putar video hasil rekaman.
  4. **Hasil yang diharapkan**: Selama halaman login aktif, tampilan aplikasi pada video rekaman akan menjadi hitam pekat (*black screen*).

### 4. Pengujian Keamanan Jaringan (HTTPS & Anti-MitM)
* **Uji HTTP Cleartext**:
  - Konfigurasi `cleartextTrafficPermitted="false"` menjamin sistem menolak semua koneksi HTTP non-aman. Jika Anda mencoba menambahkan kode koneksi HTTP polos (misal: `http://example.com`), Android akan otomatis melempar error `IOException: Cleartext HTTP traffic to example.com not permitted`.
* **Uji Man-in-the-Middle (MitM) dengan Sertifikat User**:
  1. Pasang aplikasi interceptor lalu lintas data (seperti Charles Proxy / Fiddler) dan pasang sertifikat CA dari proxy tersebut ke kategori *User Certificates* di pengaturan ponsel.
  2. Jalankan aplikasi CleanBanar dan lakukan interaksi yang memerlukan jaringan (seperti login).
  3. **Hasil yang diharapkan**: Aplikasi akan gagal terhubung dan memutus koneksi karena mendeteksi sertifikat *User CA* (bukan *System CA*). Lalu lintas data Firebase tidak akan bocor ke aplikasi proxy.

### 5. Pengujian Pencegahan Cadangan Data (Backup Disabled)
* **Uji ADB Backup**:
  1. Hubungkan perangkat/emulator ke komputer menggunakan kabel USB dengan fitur USB Debugging aktif.
  2. Jalankan perintah terminal berikut di PC Anda:
     ```bash
     adb backup -f cleanbanar.ab -noapk com.example.cleanbanar
     ```
  3. Setujui proses pencadangan di layar ponsel Anda.
  4. Periksa ukuran berkas `cleanbanar.ab` yang dihasilkan.
  5. **Hasil yang diharapkan**: Ukuran berkas cadangan akan sangat kecil (~0 KB atau hanya berisi header kosong) karena sistem Android mematuhi flag `android:allowBackup="false"` dan menolak mencadangkan data aplikasi.

### 6. Pengujian Kontrol Akses (Role-Based Access Control)
* **Uji Skenario Akun Petugas**:
  1. Masuk menggunakan akun petugas (contoh: `petugas@cleanbanar.com`).
  2. **Hasil yang diharapkan**: Dasbor Petugas dimuat (Tampilan Home). Tombol/Kartu untuk **Manajemen Petugas** tidak ditampilkan dan tidak dapat diakses sama sekali.
* **Uji Skenario Akun Admin**:
  1. Masuk menggunakan akun admin (contoh: `admin@cleanbanar.com`).
  2. **Hasil yang diharapkan**: Dasbor Admin dimuat lengkap dengan kartu akses cepat **Manajemen Petugas** yang berfungsi penuh untuk menambah atau menghapus petugas lapangan.

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

| Peran (Role) Admin | Peran (Role) Petugas |
| ------------------ | -------------------- |
| Dashboard          | Home                 |
| Statistics         | Statistik            |
| History            | History              |
| Notifications      | Notifikasi           |
| Profile            | Profil               |

*Catatan: Fungsionalitas Manajemen Petugas khusus Admin kini telah terpadu sempurna ke dalam Dasbor utama sebagai kartu akses cepat, memberikan ruang yang lebih leluasa pada bilah navigasi bawah (bottom navigation).*

## Persyaratan Sistem

| Komponen             | Keterangan                                               |
| -------------------- | -------------------------------------------------------- |
| **Minimum Android**  | Android 9.0 Pie (API Level 28)                           |
| **Target Android**   | Android 15 (API Level 35)                                |
| **Compile SDK**      | API Level 35                                             |
| **Bahasa Kotlin**    | JVM Target 17 (JDK 17)                                   |
| **Build Tools**      | Gradle (Kotlin DSL) + Android Studio                     |
| **Firebase SDK**     | Firebase BoM 33.7.0 (Auth, Realtime Database, Analytics) |
| **Security Library** | androidx.security:security-crypto 1.1.0-alpha06          |
| **Versi Aplikasi**   | 1.0 (versionCode 1)                                      |

> **Catatan Build**: Mode *release* mengaktifkan ProGuard (`isMinifyEnabled = true`) dan *resource shrinking* (`isShrinkResources = true`) untuk mengecilkan ukuran APK dan mempersulit proses *reverse engineering*. Mode *debug* dikonfigurasi dengan `isDebuggable = false` sebagai lapisan keamanan tambahan.

## Anggota Tim

Proyek ini dikerjakan sebagai bagian dari mata kuliah **Proyek Berbasis Laboratorium (PBL) Semester 4**.

| No  | Nama                | NIM          |
| --- | ------------------- | ------------ |
| 1   | *Taupik Rifki*      | *2401301078* |
| 2   | *Muhammad Rifki*    | *2401301121* |
| 3   | *Ibnu Qurtubi*      | *2401301124* |
| 4   | *Rizka Ika Maulida* | *2401301113* |
| 5   | *Mariatul Kiftiah*  | *2401301093* |

## Lisensi

Proyek ini dibuat untuk keperluan akademik dan **tidak dimaksudkan untuk penggunaan komersial**. Seluruh hak cipta dimiliki oleh tim pengembang sebagai bagian dari tugas perkuliahan.

```
Copyright © 2026 Tim CleanBanar — PBL Semester 4
Hak Cipta Dilindungi. Dilarang mendistribusikan ulang tanpa izin tertulis.
```
