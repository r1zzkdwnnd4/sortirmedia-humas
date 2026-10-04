package id.ac.unjani.humas.sortirmedia.service;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import id.ac.unjani.humas.sortirmedia.model.MediaType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mengelompokkan file RAW dan JPEG yang punya nama dasar sama (mis.
 * DSC_0012.ARW dan
 * DSC_0012.JPG) sebagai satu grup, sesuai AGENTS.md Bagian 6.3. Logika murni,
 * tanpa I/O,
 * supaya mudah di-unit-test terpisah dari FolderScanner.
 */
public final class MediaGrouping {

    private MediaGrouping() {
    }

    /**
     * Mengelompokkan item berdasarkan nama dasar (nama file tanpa ekstensi, huruf
     * kecil).
     */
    public static List<MediaGroup> group(List<MediaItem> items) {
        Map<String, List<MediaItem>> byBaseName = new LinkedHashMap<>();
        for (MediaItem item : items) {
            byBaseName.computeIfAbsent(baseName(item), k -> new ArrayList<>()).add(item);
        }

        List<MediaGroup> groups = new ArrayList<>();
        for (List<MediaItem> members : byBaseName.values()) {
            groups.add(new MediaGroup(primaryOf(members), members));
        }
        return groups;
    }

    /**
     * Nama file tanpa ekstensi, huruf kecil, dipakai sebagai kunci pengelompokan.
     */
    public static String baseName(MediaItem item) {
        String name = item.getFileName();
        int dot = name.lastIndexOf('.');
        return (dot < 0 ? name : name.substring(0, dot)).toLowerCase();
    }

    /** RAW diutamakan sebagai thumbnail utama grup, lalu PHOTO, lalu VIDEO. */
    private static MediaItem primaryOf(List<MediaItem> members) {
        return members.stream()
                .min((a, b) -> rank(a.getType()) - rank(b.getType()))
                .orElseThrow();
    }

    private static int rank(MediaType type) {
        return switch (type) {
            case RAW -> 0;
            case PHOTO -> 1;
            case VIDEO -> 2;
            case UNKNOWN -> 3;
        };
    }

    /**
     * Satu grup media: satu item "utama" untuk ditampilkan, plus seluruh
     * anggotanya.
     */
    public record MediaGroup(MediaItem primary, List<MediaItem> members) {
        public boolean isPair() {
            return members.size() > 1;
        }
    }
}