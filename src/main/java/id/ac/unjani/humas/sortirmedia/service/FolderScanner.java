package id.ac.unjani.humas.sortirmedia.service;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import id.ac.unjani.humas.sortirmedia.model.MediaType;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import javafx.concurrent.Task;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Membaca satu folder (tidak rekursif) menjadi daftar {@link MediaItem}, terurut nama file.
 * File dengan ekstensi tidak dikenal dan file tersembunyi dilewati. File yang gagal dibaca
 * (rusak, race condition terhapus saat scan) dicatat ke log dan dilewati, bukan menggagalkan
 * seluruh scan (lihat AGENTS.md Bagian 6.1 dan 6.8).
 */
public final class FolderScanner {

    private static final Logger log = LoggerFactory.getLogger(FolderScanner.class);

    private FolderScanner() {}

    /** Scan sinkron; panggil dari background thread, BUKAN dari JavaFX Application Thread. */
    public static List<MediaItem> scan(Path folder) throws IOException {
        List<MediaItem> result = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            Iterator<Path> it = stream.iterator();
            while (it.hasNext()) {
                Path file = it.next();
                try {
                    if (Files.isDirectory(file) || Files.isHidden(file)) continue;

                    MediaType type = MediaTypeResolver.resolve(file);
                    if (type == MediaType.UNKNOWN) continue;

                    long size = Files.size(file);
                    Instant modified = Files.getLastModifiedTime(file).toInstant();
                    MediaItem item = new MediaItem(file, type, size, modified);

                    if (type == MediaType.PHOTO) {
                        readPhotoResolution(file, item);
                    }
                    // RAW dan VIDEO: resolusi/durasi diisi di Fase 2 lewat PreviewProvider.

                    result.add(item);
                } catch (IOException e) {
                    log.warn("Melewati file yang gagal dibaca: {}", file, e);
                }
            }
        }

        result.sort(Comparator.comparing(MediaItem::getFileName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /** Membaca lebar/tinggi dari header file saja (tanpa decode penuh), lebih hemat memori. */
    private static void readPhotoResolution(Path file, MediaItem item) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file.toFile())) {
            if (iis == null) return;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return;

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis);
                item.setResolution(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            log.debug("Tidak bisa membaca resolusi: {}", file, e);
        }
    }

    /** Task siap pakai untuk dijalankan di executor/thread background dari UI. */
    public static Task<List<MediaItem>> scanTask(Path folder) {
        return new Task<>() {
            @Override
            protected List<MediaItem> call() throws IOException {
                return scan(folder);
            }
        };
    }
}