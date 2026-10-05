package id.ac.unjani.humas.sortirmedia.service;

import id.ac.unjani.humas.sortirmedia.model.MediaType;
import java.nio.file.Path;
import java.util.Set;

/**
 * Satu-satunya tempat yang tahu ekstensi mana masuk kategori apa (lihat AGENTS.md Bagian 6.1).
 * Kalau Humas memakai kamera dengan ekstensi RAW lain, cukup tambah di {@link #RAW_EXT}.
 */
public final class MediaTypeResolver {

    private static final Set<String> PHOTO_EXT = Set.of("jpg", "jpeg", "png");
    private static final Set<String> RAW_EXT =
            Set.of("cr2", "cr3", "nef", "arw", "dng", "orf", "rw2", "raf");
    private static final Set<String> VIDEO_EXT = Set.of("mp4", "mov", "mkv", "avi", "m4v");

    private MediaTypeResolver() {}

    public static MediaType resolve(Path file) {
        return resolve(extensionOf(file));
    }

    public static MediaType resolve(String extensionLowerCase) {
        String ext = extensionLowerCase == null ? "" : extensionLowerCase.toLowerCase();
        if (PHOTO_EXT.contains(ext)) return MediaType.PHOTO;
        if (VIDEO_EXT.contains(ext)) return MediaType.VIDEO;
        return MediaType.UNKNOWN;
    }

    private static String extensionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }
}