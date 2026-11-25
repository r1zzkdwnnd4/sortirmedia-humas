package id.ac.unjani.humas.sortirmedia.service;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import java.util.ArrayList;
import java.util.List;

/**
 * Membungkus daftar {@link MediaItem} menjadi daftar {@link MediaGroup} individual
 * sehingga file RAW dan JPEG (termasuk yang memiliki nama dasar sama seperti
 * DSC_0012.ARW dan DSC_0012.JPG) tetap tampil bersandingan di galeri.
 */
public final class MediaGrouping {

    private MediaGrouping() {
    }

    /**
     * Menampilkan setiap item media secara individual agar file RAW dan JPEG
     * dengan nama dasar yang sama tetap tampil bersandingan di galeri.
     */
    public static List<MediaGroup> group(List<MediaItem> items) {
        List<MediaGroup> groups = new ArrayList<>(items.size());
        for (MediaItem item : items) {
            groups.add(new MediaGroup(item, List.of(item)));
        }
        return groups;
    }

    /**
     * Nama file tanpa ekstensi, huruf kecil.
     */
    public static String baseName(MediaItem item) {
        String name = item.getFileName();
        int dot = name.lastIndexOf('.');
        return (dot < 0 ? name : name.substring(0, dot)).toLowerCase();
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