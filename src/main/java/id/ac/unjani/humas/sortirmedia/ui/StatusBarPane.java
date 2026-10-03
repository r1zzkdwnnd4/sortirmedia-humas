package id.ac.unjani.humas.sortirmedia.ui;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import java.nio.file.Path;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Status bar bawah: info folder yang sedang dibuka (kiri) dan info file yang sedang
 * dipilih (kanan), sesuai AGENTS.md Bagian 7.
 */
public final class StatusBarPane {

    private final Label folderInfoLabel = new Label("Belum ada folder dipilih");
    private final Label fileInfoLabel = new Label("");
    private final HBox root;

    public StatusBarPane() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        root = new HBox(10, folderInfoLabel, spacer, fileInfoLabel);
        root.setPadding(new Insets(6, 12, 6, 12));
        root.setStyle("-fx-border-color: -fx-box-border transparent transparent transparent; "
                + "-fx-border-width: 1 0 0 0;");
    }

    public HBox getView() { return root; }

    public void showFolderInfo(Path folder, List<MediaItem> items) {
        long totalBytes = items.stream().mapToLong(MediaItem::getSizeBytes).sum();
        folderInfoLabel.setText(String.format(
                "%s  •  %s  •  %d file  •  %s",
                folder.getFileName() != null ? folder.getFileName() : folder,
                folder, items.size(), formatSize(totalBytes)));
    }

    public void showFileInfo(MediaItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.getFileName()).append("  •  ").append(formatSize(item.getSizeBytes()));
        if (item.hasResolution()) {
            sb.append("  •  ").append(item.getWidth()).append("×").append(item.getHeight());
        }
        sb.append("  •  ").append(item.getExtension().toUpperCase());
        if (item.getDurationMillis() > 0) {
            sb.append("  •  ").append(formatDuration(item.getDurationMillis()));
        }
        fileInfoLabel.setText(sb.toString());
    }

    public void clearFileInfo() { fileInfoLabel.setText(""); }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }

    private String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}