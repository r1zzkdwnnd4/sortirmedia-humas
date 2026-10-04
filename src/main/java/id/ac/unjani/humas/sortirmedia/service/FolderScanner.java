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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Membaca satu folder (tidak rekursif) menjadi daftar MediaItem, terurut nama
 * file.
 *
 * <p>
 * <b>Sengaja HANYA mendaftar file</b> (path, ukuran, lastModified, jenis) tanpa
 * membaca
 * resolusi/durasi. Untuk RAW dan video, membaca metadata berarti menjalankan
 * exiftool/ffprobe
 * per file — kalau dilakukan sinkron di sini untuk ratusan file, scan folder
 * akan terasa
 * macet sebelum galeri sempat tampil. Metadata dibaca belakangan secara lazy
 * per-item lewat
 * PreviewService.readMetadata, dipanggil dari GalleryPane barengan saat
 * thumbnail dimuat
 * (AGENTS.md Bagian 6.8: target folder ±500 file tidak boleh freeze).
 */
public final class FolderScanner {

    private static final Logger log = LoggerFactory.getLogger(FolderScanner.class);

    private FolderScanner() {
    }

    /**
     * Scan sinkron; panggil dari background thread, BUKAN dari JavaFX Application
     * Thread.
     */
    public static List<MediaItem> scan(Path folder) throws IOException {
        List<MediaItem> result = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            Iterator<Path> it = stream.iterator();
            while (it.hasNext()) {
                Path file = it.next();
                try {
                    if (Files.isDirectory(file) || Files.isHidden(file))
                        continue;

                    MediaType type = MediaTypeResolver.resolve(file);
                    if (type == MediaType.UNKNOWN)
                        continue;

                    long size = Files.size(file);
                    Instant modified = Files.getLastModifiedTime(file).toInstant();
                    result.add(new MediaItem(file, type, size, modified));
                } catch (IOException e) {
                    log.warn("Melewati file yang gagal dibaca: {}", file, e);
                }
            }
        }

        result.sort(Comparator.comparing(MediaItem::getFileName, String.CASE_INSENSITIVE_ORDER));
        return result;
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