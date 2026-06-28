# CleanBanar

**CleanBanar** adalah sistem pemantauan Tempat Sampah Pintar (Smart Trash Bin) yang dibangun untuk pengelolaan sampah kampus modern. Sistem ini menyediakan dasbor interaktif secara *real-time* baik untuk **Admin** maupun **Petugas** agar dapat memantau kapasitas tempat sampah secara efisien, menerima notifikasi penting, mengkonfigurasi perangkat IoT, dan mengelola personil.

## Fitur Utama

- **Pemantauan Real-Time**: Pelacakan langsung kapasitas tempat sampah Organik & Non-Organik melalui sensor IoT.
- **Notifikasi Otomatis**: Peringatan otomatis saat tempat sampah mencapai ≥80% (Hampir Penuh) dan ≥95% (Penuh), dengan tampilan UI kartu yang indah.
- **Peringatan Waktu Inap (Stale Waste)**: Deteksi otomatis jika sampah organik (≥3 hari) atau non-organik (≥7 hari) belum dikosongkan untuk mencegah bau tak sedap dan penumpukan berlebih.
- **Manajemen & Konfigurasi Alat**: Panel admin terpusat untuk menambah, menghapus, serta mengatur parameter fisik alat seperti tinggi tong, ambang batas penuh, batas sensor jarak tangan, pemetaan pin sensor ESP32, dan tipe jaringan (Wi-Fi/Bluetooth).
- **Pengaturan Preferensi Notifikasi**: Kemudahan bagi setiap pengguna untuk menyesuaikan jenis notifikasi yang ingin diterima (Penuh, Hampir Penuh, Selesai Dikosongkan, atau Notifikasi Sistem).
- **Aktivasi Akun Mandiri (Pre-Registration)**: Admin cukup mendaftarkan email petugas. Petugas kemudian mengaktifkan akun secara mandiri dengan mengatur kata sandi mereka pada percobaan masuk pertama kali.
- **Unggah Foto Profil**: Integrasi dengan Firebase Storage yang memungkinkan pengguna untuk mengubah dan mengunggah foto profil mereka secara langsung di aplikasi.
- **Statistik**: Diagram batang visual yang menunjukkan tren kapasitas 7 hari terakhir, rata-rata mingguan, serta total kejadian penuh dan jumlah pengosongan harian.
- **Manajemen Petugas**: Panel admin terpusat (dapat diakses langsung dari Dasbor Admin) untuk menambah, melihat, dan menghapus petugas.
- **Riwayat Aktivitas**: Linimasa aktivitas universal (tersedia untuk Admin dan Petugas) yang mencatat peringatan status dan riwayat pengosongan tempat sampah.
- **Keamanan Akun**: Antarmuka masuk (Login) yang aman dengan fitur UX modern (misalnya, ikon lihat/sembunyikan kata sandi).

## Teknologi Utama (Tech Stack)

| Lapisan           | Teknologi                              |
| ----------------- | -------------------------------------- |
| **Frontend**      | Android (Kotlin, XML, ViewBinding)     |
| **Database**      | Firebase Realtime Database             |
| **Cloud Storage** | Firebase Storage (Foto Profil)         |
| **Perangkat IoT** | ESP32 (mengirim data sensor mentah)    |
| **Arsitektur**    | Hybrid — Firebase sebagai logika pusat |

## Arsitektur Sistem

```text
Sensor ESP32 → Firebase Realtime DB → Aplikasi Android (Admin / Petugas)
     ↑                ↑                       ↓
 Hanya data mentah  Sumber data utama    Tampilan UI + Aksi Pengguna
```

**Pemicu & Aliran Data:**
- **ESP32**: Hanya mengirim data jarak/kapasitas mentah dari sensor ke Firebase. Tidak ada logika keputusan di sisi mikrokontroler.
- **Firebase**: Menyimpan konfigurasi alat, status kapasitas mentah, data notifikasi, riwayat aktivitas, foto profil, dan kredensial pengguna.
- **Aplikasi Android**: Menampilkan visualisasi data secara real-time, mendeteksi ambang batas (threshold), memicu push notification melalui pemantau latar belakang (`BinObserver`), dan memfasilitasi aksi pengosongan tempat sampah.

## Struktur Database Firebase

```text
cleanbanar/
  devices/
    {deviceId}/
      id: String
      nama: String
      statusKoneksi: String ("ONLINE" / "OFFLINE")
      terakhirTerlihat: Long
      tipeJaringan: String ("WIFI" / "BLUETOOTH")
      config/
        pins/
          trigOrganik: Int, echoOrganik: Int
          trigNonOrganik: Int, echoNonOrganik: Int
          trigLuarOrganik: Int, echoLuarOrganik: Int
          trigLuarNonOrganik: Int, echoLuarNonOrganik: Int
          servoOrganik: Int, servoNonOrganik: Int
        tinggiTong: Double
        batasPenuh: Double
        batasJarakTangan: Double
      bins/
        organik/       { persentaseIsi: Int, status: String, terakhirUpdate: Long, terakhirDikosongkan: Long }
        nonOrganik/    { persentaseIsi: Int, status: String, terakhirUpdate: Long, terakhirDikosongkan: Long }
  notifications/
    {id}/              { judul, pesan, tipe, waktu, sudahDibaca }
  historyLogs/
    {id}/              { aksi, tipeSampah, idPengguna, namaLengkap, waktu }
  users/
    {id}/
      nama: String
      email: String
      peran: String
      nomorHp: String
      photoUrl: String
      pengaturan_notifikasi/
        hampir_penuh: Boolean
        penuh: Boolean
        selesai: Boolean
        sistem: Boolean
  statistics/
    daily/
      {dateKey}/       { organik: Int, nonOrganik: Int, organikEmptyCount: Int, nonOrganikEmptyCount: Int }
  public_info/
    admin_phone: String
```

## Logika Sistem Inti

### Pemicu Notifikasi (BinObserver)
- **≥80%**: Peringatan "Hampir Penuh" (Warning).
- **≥95%**: Peringatan "Penuh" (Bahaya) + otomatis mencatat riwayat log peringatan.
- **Pencegahan Duplikasi**: Perbandingan status kapasitas sebelumnya (*last-state comparison*) untuk memastikan notifikasi hanya dikirim sekali saat melewati batas threshold.
- **Pencegahan Data Anomali**: Sensor di luar rentang kewajaran 0-100% akan dibatasi (*clamped*) atau diabaikan demi menjaga keakuratan data.

### Konsistensi Data ("Tandai Telah Dikosongkan")
Saat Petugas menandai tempat sampah telah dikosongkan, `FirebaseManager.emptyBin()` secara otomatis akan:
1. Mereset kapasitas menjadi 0% dan status menjadi "Normal".
2. Mencatat waktu pengosongan terakhir (`terakhirDikosongkan`).
3. Menulis catatan riwayat log aktivitas ("pengosongan").
4. Memperbarui jumlah pengosongan harian (`organikEmptyCount` / `nonOrganikEmptyCount`) pada statistik harian.
5. Mengirim notifikasi selesai dikosongkan ke pengguna lain yang mengaktifkan preferensinya.

### Alur Aktivasi Staf Mandiri (Pre-Registration Flow)
1. **Pendaftaran Awal**: Administrator mendaftarkan email, nama, nomor telepon, dan peran (*role*) petugas baru melalui panel *Staff Management* di aplikasi. Data disimpan sementara di simpul `users/` dengan ID acak.
2. **Autentikasi & Aktivasi**: Petugas masuk menggunakan email tersebut untuk pertama kali dan mengisi kata sandi baru.
3. **Penyimpanan Kunci**: Firebase Auth memvalidasi kecocokan email pra-registrasi. Jika cocok, sistem membuat akun baru di Firebase Auth, menyalin data profil ke ID autentikasi permanen petugas, dan menghapus data pendaftaran sementara agar database tetap bersih.

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

## Cara Menjalankan Aplikasi & Konfigurasi Firebase

Ikuti panduan berikut untuk menyiapkan Firebase dan menjalankan aplikasi **CleanBanar**:

### 1. Registrasi Proyek di Firebase Console
1. Buka [Firebase Console](https://console.firebase.google.com/) dan buat proyek baru dengan nama **CleanBanar**.
2. Tambahkan aplikasi Android ke proyek Anda dengan parameter:
   - **Nama Paket Android**: `com.example.cleanbanar`
   - **Nama Panggilan Aplikasi**: `CleanBanar App`
3. Unduh berkas konfigurasi `google-services.json`.
4. Letakkan berkas `google-services.json` tersebut di dalam direktori `app/` dari proyek Anda.

### 2. Mengaktifkan Firebase Authentication
1. Pada menu navigasi Firebase Console, masuk ke **Build** -> **Authentication**.
2. Klik **Get Started**, lalu di tab **Sign-in method**, aktifkan opsi **Email/Password**.
3. Simpan konfigurasi.

### 3. Mengatur Firebase Realtime Database
1. Buka **Build** -> **Realtime Database**, lalu klik **Create Database**.
2. Pilih lokasi server database terdekat (misal: Singapore) dan pilih **Start in locked mode**.
3. Masuk ke tab **Rules** dan ubah aturan akses agar mewajibkan autentikasi bagi semua baca/tulis:
   ```json
   {
     "rules": {
       ".read": "auth != null",
       ".write": "auth != null"
     }
   }
   ```
4. Klik **Publish** untuk menyimpan perubahan.

### 4. Mengatur Firebase Storage
1. Buka **Build** -> **Storage**, klik **Get Started**, lalu pilih lokasi server dan klik **Done**.
2. Masuk ke tab **Rules** dan perbarui aturan akses agar foto profil dapat diunggah secara aman oleh pengguna yang terautentikasi:
   ```rules
   rules_version = '2';
   service firebase.storage {
     match /b/{bucket}/o {
       match /profile_pictures/{userId}.jpg {
         allow read, write: if request.auth != null && request.auth.uid == userId;
       }
     }
   }
   ```
3. Klik **Publish**.

### 5. Seeding Akun Bawaan (Default Accounts)
Untuk memudahkan akses awal, sistem memiliki mekanisme *auto-seeding* profil database:
1. Buat dua akun autentikasi secara manual di tab **Authentication** -> **Users** pada Firebase Console:
   - **Admin**: `admin@cleanbanar.com` dengan kata sandi `admin123`
   - **Petugas**: `petugas@cleanbanar.com` dengan kata sandi `petugas123`
2. Jalankan aplikasi di emulator atau perangkat fisik Anda.
3. Masuk menggunakan akun admin atau petugas yang telah dibuat.
4. Saat pertama kali masuk, aplikasi akan secara otomatis melakukan inisialisasi (*seeding*) data profil ke node Realtime Database `users/{uid}/` berdasarkan kredensial default tersebut.

### 6. Menjalankan Aplikasi
1. Buka proyek ini di **Android Studio**.
2. Pastikan koneksi internet aktif, lalu lakukan **Sync Project with Gradle Files** untuk mengunduh semua pustaka dependensi (Proyek menggunakan JDK 21).
3. Sambungkan emulator Android atau perangkat Android fisik (Min. Android 9.0 Pie / API Level 28).
4. Klik tombol **Run 'app'** di Android Studio untuk membangun dan menjalankan aplikasi.

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
| **Firebase SDK**     | Firebase BoM 33.7.0 (Auth, Realtime Database, Storage, Analytics) |
| **Security Library** | androidx.security:security-crypto 1.1.0-alpha06          |
| **Versi Aplikasi**   | 1.1.0 (versionCode 2)                                    |

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
