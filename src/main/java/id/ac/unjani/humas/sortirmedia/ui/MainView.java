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
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Menyusun FolderTreePane, GalleryPane, StatusBarPane jadi satu layout
 * (kiri/tengah/bawah)
 * sesuai AGENTS.md Bagian 7, dan menghubungkan alur: pilih folder di tree ->
 * scan di
 * background -> tampilkan di galeri + status bar.
 */
public final class MainView {

    private static final Logger log = LoggerFactory.getLogger(MainView.class);

    private final FolderTreePane folderTreePane = new FolderTreePane();
    private final GalleryPane galleryPane = new GalleryPane();
    private final StatusBarPane statusBarPane = new StatusBarPane();
    private final ViewerPane viewerPane = new ViewerPane();
    private final BorderPane mainLayout = new BorderPane();
    private final StackPane root = new StackPane();

    private List<MediaItem> allItems;
    private List<MediaItem> currentItems;
    private Path currentFolder;

    private final javafx.scene.layout.HBox filterPane = new javafx.scene.layout.HBox(10);
    private final javafx.scene.control.CheckBox[] flagChecks = new javafx.scene.control.CheckBox[5];
    private final javafx.scene.control.CheckBox unflaggedCheck = new javafx.scene.control.CheckBox("Belum Diflag");
    private final javafx.scene.control.RadioButton orRadio = new javafx.scene.control.RadioButton("OR");
    private final javafx.scene.control.RadioButton andRadio = new javafx.scene.control.RadioButton("AND");

    public MainView() {
        SplitPane splitPane = new SplitPane(folderTreePane.getView(), galleryPane.getView());
        splitPane.setDividerPositions(0.22);

        mainLayout.setCenter(splitPane);
        mainLayout.setBottom(statusBarPane.getView());
        setupFilterPane();
        mainLayout.setTop(filterPane);

        root.getChildren().add(mainLayout);
        
        viewerPane.getView().setVisible(false);
        root.getChildren().add(viewerPane.getView());

        folderTreePane.setOnFolderSelected(this::openFolder);
        galleryPane.setOnItemSelected(statusBarPane::showFileInfo);
        
        galleryPane.setOnItemAction(item -> {
            if (currentItems != null) {
                int index = currentItems.indexOf(item);
                if (index >= 0) {
                    viewerPane.getView().setVisible(true);
                    viewerPane.open(currentItems, index);
                }
            }
        });
        
        viewerPane.setOnItemChanged(statusBarPane::showFileInfo);
        viewerPane.setOnClose(item -> {
            viewerPane.getView().setVisible(false);
            galleryPane.scrollTo(item);
        });
        
        java.util.function.Consumer<MediaItem> saveFlagsHandler = item -> {
            if (currentFolder != null && allItems != null) {
                id.ac.unjani.humas.sortirmedia.service.SidecarService.saveFlags(currentFolder, allItems);
                applyFilter();
            }
        };
        galleryPane.setOnFlagChanged(saveFlagsHandler);
        viewerPane.setOnFlagChanged(saveFlagsHandler);
    }

    private void setupFilterPane() {
        filterPane.setPadding(new javafx.geometry.Insets(10));
        filterPane.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        
        javafx.scene.control.ToggleGroup group = new javafx.scene.control.ToggleGroup();
        orRadio.setToggleGroup(group);
        andRadio.setToggleGroup(group);
        orRadio.setSelected(true);
        
        for (int i = 0; i < 5; i++) {
            flagChecks[i] = new javafx.scene.control.CheckBox("Flag " + (i + 1));
            flagChecks[i].setOnAction(e -> applyFilter());
            filterPane.getChildren().add(flagChecks[i]);
        }
        
        unflaggedCheck.setOnAction(e -> applyFilter());
        orRadio.setOnAction(e -> applyFilter());
        andRadio.setOnAction(e -> applyFilter());
        
        javafx.scene.control.Button resetAllBtn = new javafx.scene.control.Button("Reset Semua Flag");
        resetAllBtn.setOnAction(e -> {
            if (allItems != null && currentFolder != null) {
                allItems.forEach(MediaItem::clearFlags);
                id.ac.unjani.humas.sortirmedia.service.SidecarService.saveFlags(currentFolder, allItems);
                applyFilter();
            }
        });
        
        filterPane.getChildren().addAll(
            new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
            unflaggedCheck,
            new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
            orRadio, andRadio,
            new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
            resetAllBtn
        );
    }

    private void applyFilter() {
        if (currentItems == null) return;
        
        boolean tempAnyFlag = false;
        for (javafx.scene.control.CheckBox cb : flagChecks) {
            if (cb.isSelected()) tempAnyFlag = true;
        }
        final boolean anyFlagChecked = tempAnyFlag;
        
        boolean unflaggedChecked = unflaggedCheck.isSelected();
        boolean isAndMode = andRadio.isSelected();
        
        List<MediaItem> filtered = currentItems.stream().filter(item -> {
            if (!anyFlagChecked && !unflaggedChecked) return true; // No filter active
            
            boolean hasAnyFlag = false;
            boolean hasAllCheckedFlags = true;
            boolean itemHasAnyFlag = item.getFlags() != 0;
            
            if (unflaggedChecked && !itemHasAnyFlag) {
                return true;
            }
            
            for (int i = 0; i < 5; i++) {
                if (flagChecks[i].isSelected()) {
                    if (item.hasFlag(i)) {
                        hasAnyFlag = true;
                    } else {
                        hasAllCheckedFlags = false;
                    }
                }
            }
            
            if (isAndMode) {
                return anyFlagChecked && hasAllCheckedFlags;
            } else {
                return anyFlagChecked && hasAnyFlag;
            }
        }).toList();
        
        galleryPane.showItems(filtered);
    }

    public StackPane getView() {
        return root;
    }

    private void openFolder(Path folder) {
        galleryPane.clear();
        statusBarPane.clearFileInfo();

        Task<List<MediaItem>> task = FolderScanner.scanTask(folder);
        task.setOnSucceeded(e -> {
            List<MediaItem> items = task.getValue();
            this.currentFolder = folder;
            this.allItems = items;
            
            id.ac.unjani.humas.sortirmedia.service.SidecarService.loadFlags(folder, items);
            List<id.ac.unjani.humas.sortirmedia.service.MediaGrouping.MediaGroup> groups = id.ac.unjani.humas.sortirmedia.service.MediaGrouping
                    .group(items);
            List<MediaItem> primaryItems = groups.stream().map(g -> g.primary()).toList();
            this.currentItems = primaryItems;
            
            applyFilter();
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