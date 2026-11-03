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
 * Bilah status di bagian bawah jendela: menampilkan informasi folder yang sedang dibuka (kiri),
 * status seleksi item (tengah), dan detail file media yang sedang dipilih (kanan).
 */
public final class StatusBarPane {

    private final Label folderInfoLabel = new Label("Belum ada folder dipilih");
    private final Label selectionInfoLabel = new Label("");
    private final Label fileInfoLabel = new Label("");
    private final HBox root;

    public StatusBarPane() {
        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        selectionInfoLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1976D2; -fx-padding: 0 8 0 8;");

        root = new HBox(10, folderInfoLabel, spacer1, selectionInfoLabel, spacer2, fileInfoLabel);
        root.setPadding(new Insets(6, 12, 6, 12));
        root.setStyle("-fx-border-color: -fx-box-border transparent transparent transparent; "
                + "-fx-border-width: 1 0 0 0;");
    }

    public HBox getView() { return root; }

    public void showFolderInfo(Path folder, List<MediaItem> items) {
        showFolderInfo(folder, 0, items);
    }

    public void showFolderInfo(Path folder, int subfolderCount, List<MediaItem> items) {
        long totalBytes = items.stream().mapToLong(MediaItem::getSizeBytes).sum();
        String folderPart = subfolderCount > 0 ? subfolderCount + " folder  •  " : "";
        folderInfoLabel.setText(String.format(
                "%s  •  %s  •  %s%d file  •  %s",
                folder.getFileName() != null ? folder.getFileName() : folder,
                folder, folderPart, items.size(), formatSize(totalBytes)));
    }

    public void showSelectedFolderInfo(Path folder) {
        String name = folder.getFileName() != null ? folder.getFileName().toString() : folder.toString();
        fileInfoLabel.setText("Folder: " + name + "  •  " + folder);
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

    public void showSelectionInfo(int folderCount, int fileCount, long totalBytes) {
        if (folderCount == 0 && fileCount == 0) {
            selectionInfoLabel.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder("Terpilih: ");
        if (folderCount > 0) {
            sb.append(folderCount).append(" folder");
        }
        if (fileCount > 0) {
            if (folderCount > 0) {
                sb.append(", ");
            }
            sb.append(fileCount).append(" file");
            if (totalBytes > 0) {
                sb.append(" (").append(formatSize(totalBytes)).append(")");
            }
        }
        selectionInfoLabel.setText(sb.toString());
    }

    public void clearSelectionInfo() {
        selectionInfoLabel.setText("");
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
