package id.ac.unjani.humas.sortirmedia.service;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import javafx.scene.image.Image;

/**
 * Antarmuka terpadu yang digunakan galeri dan viewer untuk semua jenis media
 * (foto standar, file RAW, dan video). Dengan antarmuka ini, komponen UI
 * dapat menampilkan thumbnail dan preview secara konsisten.
 */
public interface PreviewProvider {

    /**
     * Mengembalikan gambar untuk ditampilkan (thumbnail galeri atau preview
     * viewer).
     * Dipanggil dari background thread, BUKAN JavaFX Application Thread.
     * Mengembalikan
     * {@code null} kalau preview tidak bisa dibuat, dan pemanggil wajib menampilkan
     * placeholder untuk kasus itu, bukan crash.
     */
    Image getPreview(MediaItem item, int requestedSize);

    /**
     * Mengisi metadata tambahan pada item (resolusi, durasi untuk video) kalau
     * belum ada.
     * Dipanggil dari background thread, aman dipanggil berkali-kali (idempotent).
     */
    void readMetadata(MediaItem item);
}