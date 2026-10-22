package id.ac.unjani.humas.sortirmedia.ui;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import id.ac.unjani.humas.sortirmedia.service.FolderScanner;
import id.ac.unjani.humas.sortirmedia.service.MediaGrouping;
import id.ac.unjani.humas.sortirmedia.service.SidecarService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tampilan utama aplikasi yang mengintegrasikan FolderTreePane, GalleryPane,
 * dan StatusBarPane, serta menghubungkan alur kerja: pemilihan folder, pemindaian
 * di latar belakang, penampilan galeri, dan pembaruan bilah status.
 */
public final class MainView {

    private static final Logger log = LoggerFactory.getLogger(MainView.class);

    private final FolderTreePane folderTreePane = new FolderTreePane();
    private final GalleryPane galleryPane = new GalleryPane();
    private final StatusBarPane statusBarPane = new StatusBarPane();
    private final ViewerPane viewerPane = new ViewerPane();
    private final BorderPane mainLayout = new BorderPane();
    private final StackPane root = new StackPane();

    private final Button backBtn = new Button("◀");
    private final Button forwardBtn = new Button("▶");
    private final Button upFolderBtn = new Button("⬆ Naik");
    private final Label currentPathLabel = new Label("Belum ada folder dipilih");
    private final BorderPane galleryContainer = new BorderPane();

    private final List<Path> folderHistory = new ArrayList<>();
    private int historyIndex = -1;

    private List<MediaItem> allItems;
    private List<MediaItem> currentItems;
    private List<MediaGrouping.MediaGroup> allGroups;
    private Path currentFolder;

    private final javafx.scene.layout.HBox filterPane = new javafx.scene.layout.HBox(10);
    private final javafx.scene.control.CheckBox[] flagChecks = new javafx.scene.control.CheckBox[5];
    private final javafx.scene.control.CheckBox unflaggedCheck = new javafx.scene.control.CheckBox("Belum Diflag");
    private final javafx.scene.control.RadioButton orRadio = new javafx.scene.control.RadioButton("OR");
    private final javafx.scene.control.RadioButton andRadio = new javafx.scene.control.RadioButton("AND");

    public MainView() {
        backBtn.setTooltip(new Tooltip("Kembali ke folder sebelumnya (Back)"));
        backBtn.setDisable(true);
        backBtn.setOnAction(e -> {
            if (historyIndex > 0) {
                historyIndex--;
                openFolder(folderHistory.get(historyIndex), false);
            }
        });

        forwardBtn.setTooltip(new Tooltip("Maju ke folder berikutnya (Next)"));
        forwardBtn.setDisable(true);
        forwardBtn.setOnAction(e -> {
            if (historyIndex < folderHistory.size() - 1) {
                historyIndex++;
                openFolder(folderHistory.get(historyIndex), false);
            }
        });

        upFolderBtn.setTooltip(new Tooltip("Naik ke folder induk (Up)"));
        upFolderBtn.setDisable(true);
        upFolderBtn.setOnAction(e -> {
            if (currentFolder != null && currentFolder.getParent() != null) {
                openFolder(currentFolder.getParent());
            }
        });

        currentPathLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: -fx-text-base-color;");
        HBox.setHgrow(currentPathLabel, Priority.ALWAYS);

        HBox navBar = new HBox(6, backBtn, forwardBtn, upFolderBtn, currentPathLabel);
        navBar.setAlignment(Pos.CENTER_LEFT);
        navBar.setPadding(new Insets(6, 10, 6, 10));
        navBar.setStyle(
                "-fx-background-color: -fx-control-inner-background; -fx-border-color: -fx-box-border transparent -fx-box-border transparent; -fx-border-width: 0 0 1 0;");

        galleryContainer.setTop(navBar);
        galleryContainer.setCenter(galleryPane.getView());

        SplitPane splitPane = new SplitPane(folderTreePane.getView(), galleryContainer);
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
        galleryPane.setOnFolderAction(this::openFolder);
        galleryPane.setOnFolderSelected(statusBarPane::showSelectedFolderInfo);

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

        javafx.scene.control.Button exportBtn = new javafx.scene.control.Button("Export / Pindahkan...");
        exportBtn.setOnAction(e -> {
            if (currentItems == null || currentItems.isEmpty()) {
                showError("Tidak ada file yang bisa diexport.");
                return;
            }
            // Dapatkan item-item yang terfilter di galeri saat ini
            List<MediaItem> currentlyShowing = galleryPane.getVisibleMediaItems();

            if (currentlyShowing.isEmpty()) {
                showError("Tidak ada file yang sedang ditampilkan (filter kosong).");
                return;
            }

            // Map kembali ke MediaGroup
            List<MediaGrouping.MediaGroup> selectedGroups = new ArrayList<>();
            for (MediaItem item : currentlyShowing) {
                for (MediaGrouping.MediaGroup mediaGroup : allGroups) {
                    if (mediaGroup.primary() == item) {
                        selectedGroups.add(mediaGroup);
                        break;
                    }
                }
            }

            ExportDialog dialog = new ExportDialog(selectedGroups, () -> {
                // Refresh folder setelah operasi sukses
                openFolder(currentFolder);
            });
            dialog.show();
        });

        javafx.scene.control.Button uploadDriveBtn = new javafx.scene.control.Button("Upload Google Drive...");
        uploadDriveBtn.setOnAction(e -> {
            if (currentItems == null || currentItems.isEmpty()) {
                showError("Tidak ada file yang bisa diupload.");
                return;
            }
            List<MediaItem> currentlyShowing = galleryPane.getVisibleMediaItems();

            if (currentlyShowing.isEmpty()) {
                showError("Tidak ada file yang sedang ditampilkan (filter kosong).");
                return;
            }

            List<MediaGrouping.MediaGroup> selectedGroups = new ArrayList<>();
            for (MediaItem item : currentlyShowing) {
                for (MediaGrouping.MediaGroup mediaGroup : allGroups) {
                    if (mediaGroup.primary() == item) {
                        selectedGroups.add(mediaGroup);
                        break;
                    }
                }
            }

            UploadDriveDialog dialog = new UploadDriveDialog(selectedGroups, () -> {
                log.info("Upload Google Drive berhasil diselesaikan.");
            });
            dialog.show();
        });

        filterPane.getChildren().addAll(
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                unflaggedCheck,
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                orRadio, andRadio,
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                resetAllBtn,
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                exportBtn,
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                uploadDriveBtn);
    }

    private void applyFilter() {
        if (currentItems == null)
            return;

        boolean tempAnyFlag = false;
        for (javafx.scene.control.CheckBox cb : flagChecks) {
            if (cb.isSelected())
                tempAnyFlag = true;
        }
        final boolean anyFlagChecked = tempAnyFlag;

        boolean unflaggedChecked = unflaggedCheck.isSelected();
        boolean isAndMode = andRadio.isSelected();

        List<MediaItem> filtered = currentItems.stream().filter(item -> {
            if (!anyFlagChecked && !unflaggedChecked)
                return true; // No filter active

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
        openFolder(folder, true);
    }

    private void openFolder(Path folder, boolean recordHistory) {
        if (folder == null) return;

        if (recordHistory) {
            if (historyIndex >= 0 && historyIndex < folderHistory.size() - 1) {
                folderHistory.subList(historyIndex + 1, folderHistory.size()).clear();
            }
            if (folderHistory.isEmpty() || !folderHistory.get(folderHistory.size() - 1).equals(folder)) {
                folderHistory.add(folder);
                historyIndex = folderHistory.size() - 1;
            }
        }
        updateNavButtons(folder);

        galleryPane.clear();
        statusBarPane.clearFileInfo();
        currentPathLabel.setText("📁  " + folder.toAbsolutePath());

        Task<FolderScanner.ScanResult> task = FolderScanner.scanTask(folder);
        task.setOnSucceeded(e -> {
            FolderScanner.ScanResult result = task.getValue();
            List<MediaItem> items = result.mediaItems();
            this.currentFolder = folder;
            this.allItems = items;

            SidecarService.loadFlags(folder, items);
            List<MediaGrouping.MediaGroup> groups = MediaGrouping.group(items);
            this.allGroups = groups;
            List<MediaItem> primaryItems = groups.stream().map(MediaGrouping.MediaGroup::primary).toList();
            this.currentItems = primaryItems;

            galleryPane.setSubfolders(result.subfolders());
            applyFilter();
            statusBarPane.showFolderInfo(folder, result.subfolders().size(), items);
            updateNavButtons(folder);
            log.info("Folder dibuka: {} ({} subfolder, {} item, {} grup)", folder, result.subfolders().size(),
                    items.size(), groups.size());

        });
        task.setOnFailed(e -> {
            log.error("Gagal membaca folder: {}", folder, task.getException());
            showError("Tidak bisa membaca folder ini. Periksa izin akses folder.");
        });

        Thread thread = new Thread(task, "folder-scan");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateNavButtons(Path folder) {
        backBtn.setDisable(historyIndex <= 0);
        forwardBtn.setDisable(historyIndex < 0 || historyIndex >= folderHistory.size() - 1);
        upFolderBtn.setDisable(folder == null || folder.getParent() == null);
    }

    private void showError(String message) {
        Alert alert = new Alert(AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}