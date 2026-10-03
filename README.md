# Sortir Media

Aplikasi desktop (Java + JavaFX) untuk menyeleksi foto dokumentasi kegiatan di Biro Humas UNJANI, lalu mengunggah hasilnya ke Google Drive. Panduan lengkap ada di `AGENTS.md`.

## Prasyarat

- JDK 21 atau lebih baru
- Maven 3.9+

## Menjalankan

```
mvn javafx:run
```

## Menjalankan test

```
mvn test
```

## Struktur

```
src/main/java/id/ac/unjani/humas/sortirmedia/
  SortirMediaApp.java   entry point JavaFX
  Launcher.java         main class untuk JAR/jpackage
  model/                data (MediaItem, flag)
  service/              logika non-UI
  ui/                   komponen JavaFX
  util/                 utilitas
src/main/resources/logback.xml
tools/                  ffmpeg, ffprobe, exiftool (unduh manual)
```

Log ditulis ke `~/.sortir-media/logs/`.

## Folder uji (wajib disiapkan di luar repo)

Siapkan satu folder berisi kira-kira 300-500 file untuk menguji semua fase. Jangan masukkan ke git. Isi minimal:

- JPEG dari beberapa kamera/HP, dan PNG
- File RAW dari setiap model kamera yang dipakai Humas
- Video MP4 (H.264), dan satu-dua MOV/HEVC
- Pasangan RAW+JPEG dengan nama dasar yang sama (mis. `DSC_0012.ARW` dan `DSC_0012.JPG`)
- Nama file yang sama di dua subfolder berbeda (untuk uji nama bentrok)
- Nama file dengan spasi dan karakter non-ASCII
- Satu JPEG yang sengaja dirusak (potong file) dan satu file berukuran 0 byte
- Satu foto beresolusi sangat besar (> 8000 px)
- Salah satu salinan folder di flashdisk atau hard disk eksternal
