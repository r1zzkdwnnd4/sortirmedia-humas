# Alat Eksternal

Aplikasi memakai tiga alat eksternal. Binary-nya **tidak di-commit** ke git (lihat `.gitignore`), unduh sendiri lalu taruh di folder ini.

| Alat | Dipakai untuk | Mulai fase |
|---|---|---|
| `ffmpeg` | Mengambil frame depan video sebagai thumbnail | 2 |
| `ffprobe` | Membaca durasi dan resolusi video | 2 |
| `exiftool` | Mengekstrak JPEG preview yang tertanam di file RAW | 2 |

## Struktur yang diharapkan (Windows)

```
tools/
  ffmpeg.exe
  ffprobe.exe
  exiftool.exe
  exiftool_files/     <- wajib ikut ada untuk exiftool versi Windows
```

Sumber unduhan resmi: https://ffmpeg.org/download.html dan https://exiftool.org

Catatan `exiftool` untuk Windows: file unduhannya bernama `exiftool(-k).exe`. Ganti namanya menjadi `exiftool.exe`, dan simpan folder `exiftool_files` di sebelahnya.

Aplikasi mencari alat di folder ini dulu, lalu di `PATH`. Kalau tidak ditemukan, aplikasi harus menampilkan placeholder dan pesan yang jelas, bukan crash (dikerjakan di Fase 2).
