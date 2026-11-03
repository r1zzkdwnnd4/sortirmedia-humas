package id.ac.unjani.humas.sortirmedia.ui;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import id.ac.unjani.humas.sortirmedia.service.FolderScanner;
import id.ac.unjani.humas.sortirmedia.service.GoogleDriveService;
import id.ac.unjani.humas.sortirmedia.service.MediaGrouping;
import id.ac.unjani.humas.sortirmedia.service.SidecarService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tampilan utama aplikasi yang mengintegrasikan FolderTreePane, GalleryPane,
 * dan StatusBarPane, serta menghubungkan alur kerja: pemilihan folder,
 * pemindaian
 * di latar belakang, penampilan galeri, multi-seleksi file/folder, ekspor, dan
 * upload Google Drive.
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

    private final HBox filterPane = new HBox(10);
    private final CheckBox[] flagChecks = new CheckBox[5];
    private final CheckBox unflaggedCheck = new CheckBox("Belum Diflag");
    private final RadioButton orRadio = new RadioButton("OR");
    private final RadioButton andRadio = new RadioButton("AND");

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
        galleryPane.setOnSelectionChanged(this::updateSelectionStatus);

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
                SidecarService.saveFlags(currentFolder, allItems);
                applyFilter();
            }
        };
        galleryPane.setOnFlagChanged(saveFlagsHandler);
        viewerPane.setOnFlagChanged(saveFlagsHandler);

        // Global keyboard shortcut untuk seleksi
        root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (viewerPane.getView().isVisible()) {
                return;
            }
            if (event.getTarget() instanceof TextInputControl) {
                return;
            }
            if (new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN).match(event)) {
                galleryPane.selectAll();
                event.consume();
            } else if (new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN).match(event)) {
                galleryPane.deselectAll();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                galleryPane.deselectAll();
                event.consume();
            }
        });
    }

    private void updateSelectionStatus() {
        List<Path> selectedFolders = galleryPane.getSelectedFolders();
        List<MediaItem> selectedMedia = galleryPane.getSelectedMediaItems();
        long totalBytes = selectedMedia.stream().mapToLong(MediaItem::getSizeBytes).sum();
        statusBarPane.showSelectionInfo(selectedFolders.size(), selectedMedia.size(), totalBytes);
    }

    private void setupFilterPane() {
        filterPane.setPadding(new Insets(10));
        filterPane.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup group = new ToggleGroup();
        orRadio.setToggleGroup(group);
        andRadio.setToggleGroup(group);
        orRadio.setSelected(true);

        for (int i = 0; i < 5; i++) {
            flagChecks[i] = new CheckBox("Flag " + (i + 1));
            flagChecks[i].setOnAction(e -> applyFilter());
            filterPane.getChildren().add(flagChecks[i]);
        }

        unflaggedCheck.setOnAction(e -> applyFilter());
        orRadio.setOnAction(e -> applyFilter());
        andRadio.setOnAction(e -> applyFilter());

        Button resetAllBtn = new Button("Reset Semua Flag");
        resetAllBtn.setOnAction(e -> {
            if (allItems != null && currentFolder != null) {
                allItems.forEach(MediaItem::clearFlags);
                SidecarService.saveFlags(currentFolder, allItems);
                applyFilter();
            }
        });

        Button selectAllBtn = new Button("Pilih Semua");
        selectAllBtn.setTooltip(new Tooltip("Pilih semua file dan folder (Ctrl+A)"));
        selectAllBtn.setOnAction(e -> galleryPane.selectAll());

        Button deselectAllBtn = new Button("Batal Pilih");
        deselectAllBtn.setTooltip(new Tooltip("Batalkan semua pilihan (Ctrl+D / Esc)"));
        deselectAllBtn.setOnAction(e -> galleryPane.deselectAll());

        Button exportBtn = new Button("Export / Pindahkan...");
        exportBtn.setOnAction(e -> {
            List<Path> selectedFolders = galleryPane.getSelectedFolders();
            List<MediaItem> selectedMedia = galleryPane.getSelectedMediaItems();

            List<MediaItem> itemsToExport = new ArrayList<>();
            for (Path folder : selectedFolders) {
                try {
                    itemsToExport.addAll(FolderScanner.scan(folder));
                } catch (IOException ex) {
                    log.error("Gagal membaca folder untuk export: {}", folder, ex);
                }
            }
            itemsToExport.addAll(selectedMedia);

            // Jika tidak ada item terpilih secara eksplisit, gunakan seluruh item yang sedang tampil
            if (itemsToExport.isEmpty()) {
                itemsToExport = galleryPane.getVisibleMediaItems();
            }

            if (itemsToExport.isEmpty()) {
                showError("Tidak ada file yang dipilih atau sedang ditampilkan untuk diexport.");
                return;
            }

            List<MediaGrouping.MediaGroup> selectedGroups = new ArrayList<>();
            for (MediaItem item : itemsToExport) {
                boolean found = false;
                if (allGroups != null) {
                    for (MediaGrouping.MediaGroup mediaGroup : allGroups) {
                        if (mediaGroup.primary() == item || mediaGroup.members().contains(item)) {
                            if (!selectedGroups.contains(mediaGroup)) {
                                selectedGroups.add(mediaGroup);
                            }
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    selectedGroups.add(new MediaGrouping.MediaGroup(item, List.of(item)));
                }
            }

            ExportDialog dialog = new ExportDialog(selectedGroups, () -> {
                openFolder(currentFolder);
            });
            dialog.show();
        });

        Button uploadDriveBtn = new Button("Upload Google Drive...");
        uploadDriveBtn.setOnAction(e -> {
            List<Path> selectedFolders = galleryPane.getSelectedFolders();
            List<MediaItem> selectedMedia = galleryPane.getSelectedMediaItems();

            // 1. Validasi: hanya upload file/folder ketika diseleksi
            if (selectedFolders.isEmpty() && selectedMedia.isEmpty()) {
                showInfo("Silakan pilih file atau folder terlebih dahulu yang ingin diunggah ke Google Drive.\n\n"
                        + "Tip: Gunakan shortcut Ctrl+A untuk memilih semua, atau klik file/folder yang diinginkan.");
                return;
            }

            List<MediaGrouping.MediaGroup> uploadGroups = new ArrayList<>();
            String defaultActivityName = "";
            LocalDate defaultDate = null;

            if (!selectedFolders.isEmpty()) {
                // Folder diseleksi: ambil nama folder sebagai default nama kegiatan
                Path targetFolder = selectedFolders.get(0);
                String rawName = targetFolder.getFileName() != null ? targetFolder.getFileName().toString() : targetFolder.toString();
                GoogleDriveService.FolderActivityInfo info = GoogleDriveService.parseFolderActivity(rawName);
                defaultActivityName = info.activityName();
                defaultDate = info.date();

                List<MediaItem> folderItems = new ArrayList<>();
                for (Path folder : selectedFolders) {
                    try {
                        folderItems.addAll(FolderScanner.scan(folder));
                    } catch (IOException ex) {
                        log.error("Gagal membaca file dari folder terpilih: {}", folder, ex);
                    }
                }
                folderItems.addAll(selectedMedia);

                if (folderItems.isEmpty()) {
                    showError("Folder yang dipilih tidak berisi file media (foto/video) yang didukung.");
                    return;
                }
                uploadGroups = MediaGrouping.group(folderItems);

            } else {
                // File-file tertentu yang diseleksi: ambil nama kegiatan dari folder saat ini
                if (currentFolder != null && currentFolder.getFileName() != null) {
                    GoogleDriveService.FolderActivityInfo info =
                            GoogleDriveService.parseFolderActivity(currentFolder.getFileName().toString());
                    defaultActivityName = info.activityName();
                    defaultDate = info.date();
                }

                for (MediaItem item : selectedMedia) {
                    if (allGroups != null) {
                        for (MediaGrouping.MediaGroup mediaGroup : allGroups) {
                            if (mediaGroup.primary() == item || mediaGroup.members().contains(item)) {
                                if (!uploadGroups.contains(mediaGroup)) {
                                    uploadGroups.add(mediaGroup);
                                }
                                break;
                            }
                        }
                    }
                }

                if (uploadGroups.isEmpty()) {
                    showError("Tidak ada file yang bisa diupload.");
                    return;
                }
            }

            UploadDriveDialog dialog = new UploadDriveDialog(uploadGroups, defaultActivityName, defaultDate, () -> {
                log.info("Upload Google Drive berhasil diselesaikan.");
            });
            dialog.show();
        });

        filterPane.getChildren().addAll(
                new Separator(javafx.geometry.Orientation.VERTICAL),
                unflaggedCheck,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                orRadio, andRadio,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                resetAllBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                selectAllBtn,
                deselectAllBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                exportBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                uploadDriveBtn);
    }

    private void applyFilter() {
        if (currentItems == null)
            return;

        boolean tempAnyFlag = false;
        for (CheckBox cb : flagChecks) {
            if (cb.isSelected())
                tempAnyFlag = true;
        }
        final boolean anyFlagChecked = tempAnyFlag;

        boolean unflaggedChecked = unflaggedCheck.isSelected();
        boolean isAndMode = andRadio.isSelected();

        List<MediaItem> filtered = currentItems.stream().filter(item -> {
            if (!anyFlagChecked && !unflaggedChecked)
                return true; // Tidak ada filter aktif

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
        if (folder == null)
            return;

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
        statusBarPane.clearSelectionInfo();
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

    private void showInfo(String message) {
        Alert alert = new Alert(AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
