package id.ac.unjani.humas.sortirmedia.service;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import javafx.scene.image.Image;

/**
 * Satu antarmuka yang dipakai galeri dan viewer untuk semua jenis media (foto,
 * RAW, video),
 * sesuai AGENTS.md Bagian 6.3. Dengan ini, fitur di atasnya (thumbnail, zoom,
 * flagging)
 * tidak perlu tahu jenis file di baliknya.
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