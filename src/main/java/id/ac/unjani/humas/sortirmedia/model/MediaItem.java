package id.ac.unjani.humas.sortirmedia.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Representasi satu file media di galeri.
 *
 * <p>Instance ini dibuat oleh {@code FolderScanner} saat folder dibaca. Field resolusi
 * dan durasi boleh {@code -1} / {@code null} sampai informasinya berhasil dibaca
 * (mis. RAW dan video butuh pembacaan tambahan yang baru dikerjakan di Fase 2).
 *
 * <p>Field {@code flags} adalah bitmask 5 bit untuk flag 1-5 (bit 0 = flag 1).
 * Belum dipakai secara aktif sampai Fase 4; untuk saat ini selalu bernilai 0.
 */
public final class MediaItem {

    private final Path path;
    private final String fileName;
    private final MediaType type;
    private final long sizeBytes;
    private final Instant lastModified;

    // Diisi belakangan (lazy) oleh metadata reader; -1 berarti "belum diketahui".
    private volatile int width = -1;
    private volatile int height = -1;
    private volatile long durationMillis = -1;

    private volatile int flags = 0;

    public MediaItem(Path path, MediaType type, long sizeBytes, Instant lastModified) {
        this.path = Objects.requireNonNull(path, "path");
        this.fileName = path.getFileName().toString();
        this.type = Objects.requireNonNull(type, "type");
        this.sizeBytes = sizeBytes;
        this.lastModified = Objects.requireNonNull(lastModified, "lastModified");
    }

    public Path getPath() { return path; }
    public String getFileName() { return fileName; }
    public MediaType getType() { return type; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getLastModified() { return lastModified; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void setResolution(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public boolean hasResolution() {
        return width > 0 && height > 0;
    }

    public long getDurationMillis() { return durationMillis; }
    public void setDurationMillis(long durationMillis) { this.durationMillis = durationMillis; }

    /** Ekstensi file dalam huruf kecil, tanpa titik. Contoh: "jpg", "arw". */
    public String getExtension() {
        String name = fileName;
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }

    public int getFlags() { return flags; }

    /** Dipakai oleh lapisan sidecar (Fase 4) saat memuat ulang flag dari disk. */
    public void setFlags(int flags) { this.flags = flags; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MediaItem other)) return false;
        return path.equals(other.path);
    }

    @Override
    public int hashCode() { return path.hashCode(); }

    @Override
    public String toString() {
        return "MediaItem{" + fileName + ", type=" + type + ", size=" + sizeBytes + '}';
    }
}