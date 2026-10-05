package id.ac.unjani.humas.sortirmedia.service;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Preview untuk file RAW: mengekstrak JPEG preview yang tertanam memakai
 * exiftool, lalu
 * meng-cache-nya ke disk (AGENTS.md Bagian 6.3). Resolusi diambil dari JPEG
 * preview itu
 * sendiri sebagai pendekatan cepat; resolusi SENSOR asli biasanya lebih besar,
 * tapi untuk
 * kebutuhan seleksi foto ini cukup (lihat catatan di AGENTS.md Bagian 12).
 */
public final class RawPreviewProvider implements PreviewProvider {

    private static final Logger log = LoggerFactory.getLogger(RawPreviewProvider.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final PreviewCache cache;

    public RawPreviewProvider(PreviewCache cache) {
        this.cache = cache;
    }

    @Override
    public Image getPreview(MediaItem item, int requestedSize) {
        Path cached = ensureCached(item);
        if (cached == null)
            return null;
        try {
            return new Image(cached.toUri().toString(), requestedSize, requestedSize, true, true);
        } catch (Exception e) {
            log.warn("Gagal memuat preview RAW dari cache: {}", cached, e);
            return null;
        }
    }

    @Override
    public void readMetadata(MediaItem item) {
        if (item.hasResolution())
            return;
        Path cached = ensureCached(item);
        if (cached == null)
            return;

        // Dimensi diambil dari preview yang sudah di-cache (cara paling murah). Lihat
        // catatan kelas di atas soal akurasi vs resolusi sensor asli.
        try {
            Image probe = new Image(cached.toUri().toString());
            if (!probe.isError() && probe.getWidth() > 0) {
                item.setResolution((int) probe.getWidth(), (int) probe.getHeight());
            }
        } catch (Exception e) {
            log.debug("Tidak bisa membaca dimensi preview RAW: {}", cached, e);
        }
    }

    /**
     * Mengembalikan path cache berisi preview JPEG, mengekstrak dulu kalau belum
     * ada.
     */
    private Path ensureCached(MediaItem item) {
        Path cachePath = cache.cachePathFor(item);
        if (cache.exists(item))
            return cachePath;

        Optional<String> exiftool = ExternalTools.exiftool();
        if (exiftool.isEmpty())
            return null;

        if (extractWith(exiftool.get(), item.getPath(), "-PreviewImage", cachePath))
            return cachePath;
        if (extractWith(exiftool.get(), item.getPath(), "-JpgFromRaw", cachePath))
            return cachePath;
        if (extractWith(exiftool.get(), item.getPath(), "-ThumbnailImage", cachePath))
            return cachePath;

        log.warn("exiftool tidak menemukan preview tertanam di: {}", item.getPath());
        return null;
    }

    private boolean extractWith(String exiftoolPath, Path source, String tag, Path destination) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    exiftoolPath, "-b", tag, source.toAbsolutePath().toString());
            Process process = pb.start();

            Path tmp = Files.createTempFile("sortir-raw-", ".jpg");
            try {
                Files.copy(process.getInputStream(), tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                boolean finished = process.waitFor(TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    log.warn("exiftool timeout saat memproses {}", source);
                    return false;
                }
                if (Files.size(tmp) == 0)
                    return false; // tag tidak ditemukan

                Files.move(tmp, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return true;
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
            log.warn("Gagal menjalankan exiftool {} untuk {}", tag, source, e);
            return false;
        }
    }
}