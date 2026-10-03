package id.ac.unjani.humas.sortirmedia.service;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import id.ac.unjani.humas.sortirmedia.model.MediaType;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fase 1: hanya mendukung thumbnail untuk PHOTO (JPEG/PNG), dibaca langsung dari disk
 * dengan {@link Image} ber-{@code requestedWidth} agar JavaFX men-decode ukuran kecil saja.
 *
 * <p>RAW dan VIDEO mengembalikan {@code null} di fase ini; {@code GalleryPane} menampilkan
 * placeholder untuk keduanya. Fase 2 mengganti ini dengan PreviewProvider (lihat AGENTS.md
 * Bagian 6.3), dengan cache di disk.
 */
public final class ThumbnailService {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailService.class);
    public static final int THUMBNAIL_SIZE = 180;

    // Cache in-memory per sesi aplikasi (bukan cache disk; itu baru Fase 2).
    private final Map<Path, Image> cache = new ConcurrentHashMap<>();

    /** Mengembalikan thumbnail, atau {@code null} kalau jenisnya belum didukung di fase ini. */
    public Image getThumbnail(MediaItem item) {
        if (item.getType() != MediaType.PHOTO) return null;
        return cache.computeIfAbsent(item.getPath(), this::loadPhotoThumbnail);
    }

    private Image loadPhotoThumbnail(Path path) {
        try {
            return new Image(path.toUri().toString(), THUMBNAIL_SIZE, THUMBNAIL_SIZE, true, true);
        } catch (Exception e) {
            log.warn("Gagal memuat thumbnail: {}", path, e);
            return null;
        }
    }

    public void clearCache() {
        cache.clear();
    }
}