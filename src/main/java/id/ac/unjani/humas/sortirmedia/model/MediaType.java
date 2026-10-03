package id.ac.unjani.humas.sortirmedia.model;

/** Kategori jenis media yang dikenali aplikasi. */
public enum MediaType {
    PHOTO,
    RAW,
    VIDEO,
    /** Ekstensi tidak dikenali; file jenis ini diabaikan oleh FolderScanner. */
    UNKNOWN
}