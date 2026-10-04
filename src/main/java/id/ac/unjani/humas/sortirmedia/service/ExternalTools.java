package id.ac.unjani.humas.sortirmedia.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mencari lokasi executable eksternal (ffmpeg, ffprobe, exiftool) sesuai
 * AGENTS.md Bagian 6.3:
 * cari dulu di folder tools/ di samping aplikasi, baru fallback ke PATH sistem.
 *
 * <p>
 * Tidak pernah melempar exception kalau tidak ditemukan — pemanggil
 * (RawPreviewProvider,
 * VideoPreviewProvider) bertanggung jawab menampilkan placeholder, bukan crash.
 */
public final class ExternalTools {

    private static final Logger log = LoggerFactory.getLogger(ExternalTools.class);
    private static final Map<String, Optional<String>> CACHE = new ConcurrentHashMap<>();

    private ExternalTools() {
    }

    public static Optional<String> ffmpeg() {
        return locate("ffmpeg");
    }

    public static Optional<String> ffprobe() {
        return locate("ffprobe");
    }

    public static Optional<String> exiftool() {
        return locate("exiftool");
    }

    private static Optional<String> locate(String name) {
        return CACHE.computeIfAbsent(name, ExternalTools::resolve);
    }

    private static Optional<String> resolve(String name) {
        String exeName = isWindows() ? name + ".exe" : name;

        // 1. Folder tools/ di samping direktori kerja aplikasi (lihat tools/README.md).
        Path bundled = Path.of("tools", exeName);
        if (Files.isExecutable(bundled)) {
            log.debug("Menemukan {} di {}", name, bundled.toAbsolutePath());
            return Optional.of(bundled.toAbsolutePath().toString());
        }

        // 2. PATH sistem.
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                Path candidate = Path.of(dir, exeName);
                if (Files.isExecutable(candidate)) {
                    log.debug("Menemukan {} di PATH: {}", name, candidate);
                    return Optional.of(candidate.toString());
                }
            }
        }

        log.warn("Tidak menemukan executable '{}'. Letakkan di folder tools/ atau PATH.", name);
        return Optional.empty();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Dipakai di unit test agar cache tidak membocorkan hasil antar test. */
    static void clearCacheForTesting() {
        CACHE.clear();
    }
}