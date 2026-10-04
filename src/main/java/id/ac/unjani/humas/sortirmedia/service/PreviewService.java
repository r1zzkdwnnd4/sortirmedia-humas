package id.ac.unjani.humas.sortirmedia.service;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import javafx.scene.image.Image;

/**
 * Titik akses tunggal untuk galeri dan viewer mendapatkan preview/metadata,
 * tanpa perlu tahu
 * jenis file di baliknya (AGENTS.md Bagian 6.3). Menggantikan ThumbnailService
 * dari Fase 1.
 */
public final class PreviewService {

    public static final int THUMBNAIL_SIZE = 180;

    private final PhotoPreviewProvider photoProvider = new PhotoPreviewProvider();
    private final RawPreviewProvider rawProvider;
    private final VideoPreviewProvider videoProvider;

    public PreviewService() {
        PreviewCache cache = new PreviewCache();
        this.rawProvider = new RawPreviewProvider(cache);
        this.videoProvider = new VideoPreviewProvider(cache);
    }

    public Image getThumbnail(MediaItem item) {
        return providerFor(item).getPreview(item, THUMBNAIL_SIZE);
    }

    public Image getPreview(MediaItem item, int requestedSize) {
        return providerFor(item).getPreview(item, requestedSize);
    }

    public void readMetadata(MediaItem item) {
        providerFor(item).readMetadata(item);
    }

    private PreviewProvider providerFor(MediaItem item) {
        return switch (item.getType()) {
            case PHOTO -> photoProvider;
            case RAW -> rawProvider;
            case VIDEO -> videoProvider;
            case UNKNOWN -> photoProvider; // tidak pernah terjadi: UNKNOWN disaring di FolderScanner
        };
    }
}