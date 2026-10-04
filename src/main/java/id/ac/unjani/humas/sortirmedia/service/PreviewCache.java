package id.ac.unjani.humas.sortirmedia.service;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache preview JPEG hasil ekstraksi RAW/video di disk, sesuai AGENTS.md Bagian
 * 6.3.
 * Kunci cache: hash dari path absolut + ukuran file + lastModified, supaya
 * cache otomatis
 * tidak valid lagi kalau file sumber berubah.
 */
public final class PreviewCache {

    private static final Logger log = LoggerFactory.getLogger(PreviewCache.class);
    private final Path cacheDir;

    public PreviewCache() {
        this(Path.of(System.getProperty("user.home"), ".sortir-media", "cache"));
    }

    public PreviewCache(Path cacheDir) {
        this.cacheDir = cacheDir;
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            log.warn("Tidak bisa membuat folder cache {}, preview tidak akan di-cache", cacheDir, e);
        }
    }

    /** Path file cache (belum tentu ada isinya) untuk sebuah MediaItem. */
    public Path cachePathFor(MediaItem item) {
        return cacheDir.resolve(keyFor(item) + ".jpg");
    }

    public boolean exists(MediaItem item) {
        Path path = cachePathFor(item);
        return Files.isRegularFile(path) && isNonEmpty(path);
    }

    private boolean isNonEmpty(Path path) {
        try {
            return Files.size(path) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private String keyFor(MediaItem item) {
        String raw = item.getPath().toAbsolutePath() + "|" + item.getSizeBytes() + "|"
                + item.getLastModified().toEpochMilli();
        return sha1(raw);
    }

    private String sha1(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-1 selalu tersedia di JDK standar
        }
    }
}