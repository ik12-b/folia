# Folia

**Folia** adalah aplikasi Android untuk transkripsi manuskrip Arab klasik (kitab kuning) — OCR/HTR (*Handwritten Text Recognition*) yang berjalan sepenuhnya *on-device*, tanpa mengirim gambar manuskrip ke server mana pun.

Dibangun untuk mendukung digitalisasi naskah klasik: fiqh, hadits, ilmu falak, dan tradisi keilmuan Islam lainnya — termasuk tata letak halaman yang khas seperti kolom matan-syarah-marginalia, bingkai dekoratif, dan ilustrasi geometris pada kitab-kitab ilmu falak/handasah.

---

## Fitur

- **Deteksi baris teks** berbasis model PP-OCRv5 (DBNet), dengan penanganan tata letak multi-kolom (matan, syarah, marginalia kanan/kiri) sesuai konvensi baca manuskrip klasik.
- **Pengenalan teks (HTR)** berbasis model Kraken/Muharaf, dilatih untuk aksara Arab pada manuskrip tulisan tangan.
- **Deteksi orientasi baris miring/vertikal** untuk marginalia yang ditulis diagonal — umum dijumpai pada catatan pinggir manuskrip.
- **Deteksi elemen non-tekstual** — garis/bingkai pemisah antar-kolom dan ilustrasi geometris (diagram falak, dsb.) direkam sebagai bagian dari struktur transkripsi, bukan dibuang sebagai "noise".
- **Model OCR dapat diganti** (*Model Manager*) — mendukung model ONNX kustom untuk deteksi maupun pengenalan, dengan validasi bentuk input otomatis saat model diaktifkan.
- **Ekspor** ke beberapa format melalui `ScholarlyExportService` (mis. PDF dengan overlay teks, PAGE-XML/ALTO, teks polos).
- Basis data lokal (Room) untuk menyimpan dokumen, halaman, dan hasil transkripsi.

## Status proyek

Proyek ini aktif dikembangkan. Beberapa catatan jujur soal keterbatasan saat ini:

- Akurasi transkripsi (CER) bervariasi cukup besar tergantung kualitas gaya tulisan tangan pada manuskrip sumber — realistis di kisaran puluhan persen pada kolom teks utama yang jelas, dan lebih tinggi pada marginalia yang sangat kecil/padat.
- Dukungan model ganti-pakai (selain model bawaan) sudah divalidasi secara struktural (validasi bentuk input/output, fallback aman), tetapi preprocessing untuk arsitektur model yang sangat berbeda dari model bawaan (mis. jumlah channel atau normalisasi piksel yang berbeda) mungkin memerlukan penyesuaian manual.
- Deteksi bullet/rosette dekoratif yang menyatu di tengah baris teks (bukan berdiri sebagai elemen terpisah) belum tertangani.

## Arsitektur singkat

```
app/src/main/java/com/example/
├── data/            # Room database, DAO, repository
├── domain/
│   ├── ocr/         # Pipeline deteksi & pengenalan (ONNX Runtime)
│   ├── ScholarlyExportService.kt
│   └── TextAlignmentEngine.kt
└── ui/
    ├── screens/     # Layar: Home, Workspace, Viewer/Studio, Writer, Settings
    ├── components/  # Dialog & komponen (Model Manager, PP-OCR config, dsb.)
    └── viewmodel/    # FoliaViewModel
```

Model ONNX bawaan (deteksi PP-OCRv5 dan pengenalan Muharaf) disertakan di `app/src/main/assets/models/`. Inferensi berjalan sepenuhnya lokal melalui [ONNX Runtime Mobile](https://onnxruntime.ai/).

### Identitas visual

Launcher icon (adaptive icon, `app/src/main/res/drawable/ic_launcher_*.png`) dan splash screen (`androidx.core:core-splashscreen`, lihat `MainActivity.kt` dan `Theme.Folia.Splash` di `themes.xml`) memakai motif buku terbuka bermotif daun sebagai mark utama, dengan palet navy (`#0E1B2E`) dan emas (`#B45309`-an) yang senada dengan tema "manuskrip berhias" pada UI aplikasi. Splash screen menahan tampilannya sampai proses pemanasan model ONNX dan inisialisasi database selesai (lihat `FoliaViewModel.isAppReady`), bukan sekadar animasi berdurasi tetap.

---

## Menjalankan proyek secara lokal

**Prasyarat:** [Android Studio](https://developer.android.com/studio) (versi terbaru yang mendukung AGP 9.x), JDK 17 (AGP 9.1.x mensyaratkan minimum JDK 17).

1. Clone repository ini dan buka dengan Android Studio (**File → Open**, pilih folder proyek).
2. Biarkan Android Studio menyelesaikan Gradle sync dan mengunduh dependency yang diperlukan.
3. **Siapkan signing config untuk build debug** (lihat penjelasan di bawah) — tanpa langkah ini, build debug akan gagal karena mencari `debug.keystore` yang sengaja tidak disertakan di repo.
4. Jalankan aplikasi pada emulator atau perangkat fisik (disarankan minimum Android 7.0 / API 24, RAM ≥ 4GB untuk performa inferensi ONNX yang wajar).

### Menyiapkan signing config (wajib untuk build debug)

`app/build.gradle.kts` mereferensikan dua signing config:

- `debugConfig` — mencari `debug.keystore` di root proyek (**tidak disertakan di repo ini**, lihat `.gitignore`).
- `release` — membaca path keystore dan kredensial dari environment variable `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`.

Untuk build debug lokal, pilih salah satu:

- **Opsi A (tercepat):** hapus baris `signingConfig = signingConfigs.getByName("debugConfig")` pada blok `buildTypes { debug { ... } }` di `app/build.gradle.kts`, lalu biarkan Android Studio memakai debug-keystore bawaannya sendiri.
- **Opsi B:** buat debug keystore Anda sendiri (`keytool -genkey -v -keystore debug.keystore -storetype JKS -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000`) dan letakkan di root proyek dengan kredensial yang cocok dengan yang tertulis di `build.gradle.kts` (`storePassword`/`keyAlias`/`keyPassword` = `android`).

Untuk build **release**, siapkan keystore upload Anda sendiri dan set environment variable di atas — **jangan pernah** meng-commit file keystore atau kredensialnya ke repository.

### Build dari command line (tanpa Android Studio)

Repo ini belum menyertakan `gradlew`/`gradlew.bat` (Gradle Wrapper). Untuk membuatnya sekali (disarankan, agar versi Gradle konsisten untuk semua kontributor):

```bash
gradle wrapper --gradle-version 9.2.1
```

perintah ini akan menghasilkan `gradlew`, `gradlew.bat`, dan `gradle/wrapper/gradle-wrapper.jar` — commit ketiganya. Setelah itu, gunakan `./gradlew <task>` seperti proyek Android pada umumnya (mis. `./gradlew assembleDebug`). Sebelum wrapper dibuat, Anda perlu Gradle 9.2.1 terinstal secara lokal untuk menjalankan perintah di atas (lihat [gradle.org/install](https://gradle.org/install/)).

---

## Continuous Integration

Workflow GitHub Actions (`.github/workflows/android-build.yml`) berjalan otomatis pada setiap push/PR ke branch `main`: menjalankan lint, unit test, dan build APK debug, lalu mengunggah APK dan laporan sebagai artifact. Karena repo ini belum menyertakan Gradle Wrapper (lihat di atas), workflow memakai [`gradle/actions/setup-gradle`](https://github.com/gradle/actions) dengan versi Gradle eksplisit alih-alih `./gradlew`. Jika Anda menambahkan Gradle Wrapper ke repo di kemudian hari, sesuaikan `run: gradle <task>` di file workflow menjadi `run: ./gradlew <task>`.

---

## Mengunggah ke GitHub

Folder ini belum berupa git repository (belum ada riwayat commit). Untuk mengunggahnya:

```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin <URL_REPO_GITHUB_ANDA>
git push -u origin main
```

Karena belum ada riwayat commit sebelumnya, tidak ada risiko file sensitif (keystore, `.env`) "bocor" lewat commit lama — `.gitignore` di repo ini sudah mencakup pola umum untuk itu. Tetap periksa `git status` sebelum `git add .` pertama kali untuk memastikan tidak ada file tak terduga yang ikut ter-stage.

---

## Sebelum publikasi (checklist untuk pengelola repo)

- [ ] **Lisensi** — repo ini belum menyertakan file `LICENSE`. Tambahkan lisensi open-source pilihan Anda (mis. MIT, Apache 2.0, GPL) agar syarat penggunaan ulang kode ini jelas bagi kontributor/pengguna lain.
- [ ] Tinjau kembali `versionCode`/`versionName` di `app/build.gradle.kts` sebelum rilis pertama.
- [ ] Model ONNX bawaan di `app/src/main/assets/models/` berukuran total ~6.3MB (`muharaf_rec_best_int8.onnx` ~4.9MB, `ppocrv5_det_int8.onnx` ~1.4MB) — masih dalam batas wajar untuk Git biasa, tetapi jika Anda menambahkan model tambahan yang jauh lebih besar (mis. model recognition kelas "medium" seperti kraken PP-OCRv6, umumnya puluhan MB), pertimbangkan [Git LFS](https://git-lfs.com/).
- [ ] `namespace` di `app/build.gradle.kts` dan struktur package Kotlin masih memakai `com.example.*` — ganti ke namespace milik Anda sendiri jika ingin publikasi resmi (ini perubahan yang lebih luas dan sengaja tidak dilakukan otomatis karena menyentuh path package di seluruh source, bukan cuma satu baris konfigurasi).

## Kontribusi

Issue dan pull request dipersilakan. Untuk perubahan besar pada pipeline OCR (deteksi/pengenalan/segmentasi), mohon sertakan contoh sebelum-sesudah pada gambar manuskrip nyata di deskripsi PR — ini pipeline yang sensitif terhadap regresi visual yang sulit terlihat hanya dari membaca kode.

## Lisensi

*(Belum ditentukan — lihat checklist di atas.)*
