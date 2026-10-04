package id.ac.unjani.humas.sortirmedia.service;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Preview untuk video: hanya mengambil SATU frame depan lewat ffmpeg sebagai
 * thumbnail,
 * TIDAK PERNAH memutar video (keputusan final pengguna, AGENTS.md Bagian 5 #2).
 * Durasi dan
 * resolusi dibaca lewat ffprobe.
 */
public final class VideoPreviewProvider implements PreviewProvider {

    private static final Logger log = LoggerFactory.getLogger(VideoPreviewProvider.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final PreviewCache cache;

    public VideoPreviewProvider(PreviewCache cache) {
        this.cache = cache;
    }

    @Override
    public Image getPreview(MediaItem item, int requestedSize) {
        Path cached = ensureFrameCached(item);
        if (cached == null)
            return null;
        try {
            return new Image(cached.toUri().toString(), requestedSize, requestedSize, true, true);
        } catch (Exception e) {
            log.warn("Gagal memuat frame video dari cache: {}", cached, e);
            return null;
        }
    }

    @Override
    public void readMetadata(MediaItem item) {
        if (item.hasResolution() && item.getDurationMillis() > 0)
            return;

        Optional<String> ffprobe = ExternalTools.ffprobe();
        if (ffprobe.isEmpty())
            return;

        try {
            // -of csv=p=0 -> satu baris ringkas "width,height,duration", tidak butuh parser
            // JSON.
            ProcessBuilder pb = new ProcessBuilder(
                    ffprobe.get(), "-v", "error",
                    "-select_streams", "v:0",
                    "-show_entries", "stream=width,height:format=duration",
                    "-of", "csv=p=0",
                    item.getPath().toAbsolutePath().toString());
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce("", (a, b) -> a + "\n" + b).trim();
            }
            boolean finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("ffprobe timeout untuk {}", item.getPath());
                return;
            }
            parseProbeOutput(output, item);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
            log.warn("Gagal menjalankan ffprobe untuk {}", item.getPath(), e);
        }
    }

    private void parseProbeOutput(String output, MediaItem item) {
        if (output.isBlank())
            return;
        String firstLine = output.lines().findFirst().orElse("");
        String[] parts = firstLine.split(",");
        try {
            if (parts.length >= 2) {
                item.setResolution(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
            }
            if (parts.length >= 3 && !parts[2].isBlank()) {
                item.setDurationMillis((long) (Double.parseDouble(parts[2].trim()) * 1000));
            }
        } catch (NumberFormatException e) {
            log.debug("Output ffprobe tidak terbaca untuk {}: '{}'", item.getPath(), firstLine);
        }
    }

    private Path ensureFrameCached(MediaItem item) {
        Path cachePath = cache.cachePathFor(item);
        if (cache.exists(item))
            return cachePath;

        Optional<String> ffmpeg = ExternalTools.ffmpeg();
        if (ffmpeg.isEmpty())
            return null;

        try {
            Path tmp = Files.createTempFile("sortir-video-", ".jpg");
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        ffmpeg.get(), "-y", "-ss", "0",
                        "-i", item.getPath().toAbsolutePath().toString(),
                        "-frames:v", "1", "-q:v", "4",
                        tmp.toAbsolutePath().toString());
                pb.redirectErrorStream(true);
                Process process = pb.start();
                process.getInputStream().readAllBytes(); // buang output verbose ffmpeg

                boolean finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    log.warn("ffmpeg timeout untuk {}", item.getPath());
                    return null;
                }
                if (Files.size(tmp) == 0) {
                    log.warn("ffmpeg tidak menghasilkan frame untuk {}", item.getPath());
                    return null;
                }

                Files.move(tmp, cachePath, StandardCopyOption.REPLACE_EXISTING);
                return cachePath;
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
            log.warn("Gagal mengambil frame video: {}", item.getPath(), e);
            return null;
        }
    }
}