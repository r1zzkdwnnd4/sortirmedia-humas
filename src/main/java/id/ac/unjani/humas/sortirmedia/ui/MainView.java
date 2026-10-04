package id.ac.unjani.humas.sortirmedia.ui;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import id.ac.unjani.humas.sortirmedia.service.FolderScanner;
import java.nio.file.Path;
import java.util.List;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Menyusun FolderTreePane, GalleryPane, StatusBarPane jadi satu layout (kiri/tengah/bawah)
 * sesuai AGENTS.md Bagian 7, dan menghubungkan alur: pilih folder di tree -> scan di
 * background -> tampilkan di galeri + status bar.
 */
public final class MainView {

    private static final Logger log = LoggerFactory.getLogger(MainView.class);

    private final FolderTreePane folderTreePane = new FolderTreePane();
    private final GalleryPane galleryPane = new GalleryPane();
    private final StatusBarPane statusBarPane = new StatusBarPane();
    private final BorderPane root = new BorderPane();

    public MainView() {
        SplitPane splitPane = new SplitPane(folderTreePane.getView(), galleryPane.getView());
        splitPane.setDividerPositions(0.22);

        root.setCenter(splitPane);
        root.setBottom(statusBarPane.getView());

        folderTreePane.setOnFolderSelected(this::openFolder);
        galleryPane.setOnItemSelected(statusBarPane::showFileInfo);
    }

    public BorderPane getView() { return root; }

    private void openFolder(Path folder) {
        galleryPane.clear();
        statusBarPane.clearFileInfo();

        Task<List<MediaItem>> task = FolderScanner.scanTask(folder);
        task.setOnSucceeded(e -> {
            List<MediaItem> items = task.getValue();
            List<id.ac.unjani.humas.sortirmedia.service.MediaGrouping.MediaGroup> groups = id.ac.unjani.humas.sortirmedia.service.MediaGrouping.group(items);
            List<MediaItem> primaryItems = groups.stream().map(g -> g.primary()).toList();
            galleryPane.showItems(primaryItems);
            statusBarPane.showFolderInfo(folder, items);
            log.info("Folder dibuka: {} ({} item, {} grup)", folder, items.size(), groups.size());
        });
        task.setOnFailed(e -> {
            log.error("Gagal membaca folder: {}", folder, task.getException());
            showError("Tidak bisa membaca folder ini. Periksa izin akses folder.");
        });

        Thread thread = new Thread(task, "folder-scan");
        thread.setDaemon(true);
        thread.start();
    }

    private void showError(String message) {
        Alert alert = new Alert(AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}