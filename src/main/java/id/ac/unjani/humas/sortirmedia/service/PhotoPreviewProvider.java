package id.ac.unjani.humas.sortirmedia.service;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import java.io.IOException;
import java.util.Iterator;
import javafx.scene.image.Image;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Preview untuk JPEG/PNG: dibaca langsung dari disk, decode pada ukuran yang
 * diminta saja.
 */
public final class PhotoPreviewProvider implements PreviewProvider {

    private static final Logger log = LoggerFactory.getLogger(PhotoPreviewProvider.class);

    @Override
    public Image getPreview(MediaItem item, int requestedSize) {
        try {
            return new Image(item.getPath().toUri().toString(), requestedSize, requestedSize, true, true);
        } catch (Exception e) {
            log.warn("Gagal memuat preview foto: {}", item.getPath(), e);
            return null;
        }
    }

    @Override
    public void readMetadata(MediaItem item) {
        if (item.hasResolution())
            return;
        try (ImageInputStream iis = ImageIO.createImageInputStream(item.getPath().toFile())) {
            if (iis == null)
                return;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext())
                return;

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                item.setResolution(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            log.debug("Tidak bisa membaca resolusi foto: {}", item.getPath(), e);
        }
    }
}