package id.ac.unjani.humas.sortirmedia.ui;

import com.google.api.services.drive.Drive;
import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import id.ac.unjani.humas.sortirmedia.service.GoogleDriveService;
import id.ac.unjani.humas.sortirmedia.service.MediaGrouping;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dialog untuk mengunggah folder hasil sortir ke Google Drive (Fase 6).
 * Memuat:
 * - Login OAuth2 (Desktop) dengan token tersimpan
 * - Input nama kegiatan & DatePicker (default hari ini)
 * - Pembuatan/pencarian folder bulan (8. Agustus, dst.) di bawah folder induk Humas
 * - Pembuatan folder kegiatan berformat "YYMMDD - Nama Kegiatan"
 * - Resumable upload, progress bar, dan retry
 */
public class UploadDriveDialog {

    private static final Logger log = LoggerFactory.getLogger(UploadDriveDialog.class);

    private final Stage stage = new Stage();
    private final List<MediaGrouping.MediaGroup> groups;
    private final Runnable onSuccess;
    private final GoogleDriveService driveService = GoogleDriveService.getInstance();

    private final TextField activityField = new TextField();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final Label folderPreviewLabel = new Label();
    private final Label yearFolderLabel = new Label();
    private final Label monthFolderLabel = new Label();
    private final Label hierarchyPreviewLabel = new Label();
    private final TextField humasFolderIdField = new TextField();

    private final Label accountStatusLabel = new Label("Memeriksa status login...");
    private final Button loginBtn = new Button("Login Google");
    private final Button logoutBtn = new Button("Ganti Akun");
    private final Button credentialsBtn = new Button("Pilih credentials.json...");

    private final CheckBox includeRawCheckBox = new CheckBox("Sertakan semua file (termasuk file RAW/pasangan)");
    private final Label fileSummaryLabel = new Label();

    private final ProgressBar fileProgressBar = new ProgressBar(0);
    private final Label fileProgressLabel = new Label("File: Menunggu...");
    private final ProgressBar totalProgressBar = new ProgressBar(0);
    private final Label totalProgressLabel = new Label("Total: Menunggu...");
    private final TextArea logArea = new TextArea();

    private final Button uploadButton = new Button("Mulai Upload");
    private final Button cancelButton = new Button("Batal");

    private Task<Void> currentUploadTask;

    public UploadDriveDialog(List<MediaGrouping.MediaGroup> groups, Runnable onSuccess) {
        this.groups = groups;
        this.onSuccess = onSuccess;

        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Upload ke Google Drive");
        stage.setMinWidth(550);
        stage.setMinHeight(650);

        VBox root = new VBox(12);
        root.setPadding(new Insets(18));

        // 1. Bagian Akun Google
        VBox accountBox = createAccountSection();

        // 2. Bagian Info Kegiatan & Folder
        VBox activityBox = createActivitySection();

        // 3. Ringkasan File
        includeRawCheckBox.setSelected(true);
        includeRawCheckBox.setOnAction(e -> updateFileSummary());
        updateFileSummary();

        // 4. Progress & Log
        fileProgressBar.setPrefWidth(Double.MAX_VALUE);
        totalProgressBar.setPrefWidth(Double.MAX_VALUE);
        logArea.setEditable(false);
        logArea.setPrefRowCount(4);
        logArea.setWrapText(true);

        VBox progressBox = new VBox(6,
                totalProgressLabel, totalProgressBar,
                fileProgressLabel, fileProgressBar,
                new Label("Aktivitas:"), logArea
        );

        // 5. Tombol Aksi
        HBox buttonBox = new HBox(10, uploadButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        uploadButton.setOnAction(e -> startUpload());
        cancelButton.setOnAction(e -> cancelOrClose());

        root.getChildren().addAll(
                accountBox,
                new Separator(),
                activityBox,
                new Separator(),
                new Label("File Hasil Sortir:"),
                includeRawCheckBox,
                fileSummaryLabel,
                new Separator(),
                progressBox,
                buttonBox
        );

        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> cancelOrClose());

        updateFolderPreviews();
        refreshAccountStatusAsync();
    }

    private VBox createAccountSection() {
        VBox box = new VBox(8);
        box.getChildren().add(new Label("Akun Google Drive:"));

        HBox accountRow = new HBox(10, accountStatusLabel, loginBtn, logoutBtn, credentialsBtn);
        accountRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(accountStatusLabel, Priority.ALWAYS);

        loginBtn.setOnAction(e -> doLogin());
        logoutBtn.setOnAction(e -> doLogout());
        credentialsBtn.setOnAction(e -> chooseCredentialsFile());

        logoutBtn.setVisible(false);
        box.getChildren().add(accountRow);
        return box;
    }

    private VBox createActivitySection() {
        VBox box = new VBox(8);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        grid.add(new Label("Tanggal Kegiatan:"), 0, 0);
        grid.add(datePicker, 1, 0);

        grid.add(new Label("Nama Kegiatan:"), 0, 1);
        activityField.setPromptText("Contoh: Peliputan Dies Natalis");
        grid.add(activityField, 1, 1);

        grid.add(new Label("Folder Induk Humas (ID/Nama):"), 0, 2);
        String savedFolderId = driveService.getConfiguredHumasFolderId();
        humasFolderIdField.setText(savedFolderId.isEmpty() ? "Humas" : savedFolderId);
        humasFolderIdField.setPromptText("Nama 'Humas' atau ID folder Drive");
        grid.add(humasFolderIdField, 1, 2);

        grid.add(new Label("Folder Tahun Drive:"), 0, 3);
        grid.add(yearFolderLabel, 1, 3);

        grid.add(new Label("Folder Bulan Drive:"), 0, 4);
        grid.add(monthFolderLabel, 1, 4);

        grid.add(new Label("Nama Folder Kegiatan:"), 0, 5);
        folderPreviewLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #1976D2;");
        grid.add(folderPreviewLabel, 1, 5);

        grid.add(new Label("Target Hirarki Drive:"), 0, 6);
        hierarchyPreviewLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2E7D32;");
        grid.add(hierarchyPreviewLabel, 1, 6);

        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> updateFolderPreviews());
        activityField.textProperty().addListener((obs, oldVal, newVal) -> updateFolderPreviews());
        humasFolderIdField.textProperty().addListener((obs, oldVal, newVal) -> updateFolderPreviews());
        updateFolderPreviews();

        box.getChildren().add(grid);
        return box;
    }

    private void updateFolderPreviews() {
        LocalDate date = datePicker.getValue() != null ? datePicker.getValue() : LocalDate.now();
        int year = date.getYear();
        yearFolderLabel.setText(String.valueOf(year));

        String monthName = GoogleDriveService.getMonthFolderName(date.getMonthValue());
        monthFolderLabel.setText(monthName);

        String activity = activityField.getText();
        String folderName = GoogleDriveService.formatActivityFolderName(date, activity);
        folderPreviewLabel.setText(folderName);

        String humas = humasFolderIdField.getText() != null ? humasFolderIdField.getText().trim() : "";
        if (humas.isEmpty()) humas = "Humas";
        hierarchyPreviewLabel.setText("Drive > " + humas + " > " + year + " > " + monthName + " > " + folderName);
    }

    private void updateFileSummary() {
        List<Path> files = collectFilesToUpload();
        long totalBytes = 0;
        for (Path p : files) {
            try {
                totalBytes += java.nio.file.Files.size(p);
            } catch (Exception ignored) {}
        }
        double mb = totalBytes / (1024.0 * 1024.0);
        fileSummaryLabel.setText(String.format("%d file terpilih (Total: %.1f MB)", files.size(), mb));
    }

    private List<Path> collectFilesToUpload() {
        List<Path> result = new ArrayList<>();
        boolean includeAll = includeRawCheckBox.isSelected();

        for (MediaGrouping.MediaGroup g : groups) {
            if (includeAll) {
                for (MediaItem item : g.members()) {
                    result.add(item.getPath());
                }
            } else {
                result.add(g.primary().getPath());
            }
        }
        return result;
    }

    private void refreshAccountStatusAsync() {
        Thread thread = new Thread(() -> {
            boolean hasCreds = driveService.isCredentialsAvailable();
            boolean authorized = driveService.isAuthorized();
            String userEmail = authorized ? driveService.getUserAccountEmail() : null;

            Platform.runLater(() -> {
                if (!hasCreds) {
                    accountStatusLabel.setText("Belum ada file credentials.json.");
                    loginBtn.setDisable(true);
                    logoutBtn.setVisible(false);
                    credentialsBtn.setVisible(true);
                } else if (authorized) {
                    accountStatusLabel.setText(userEmail != null ? "Login: " + userEmail : "Terhubung dengan Google Drive");
                    loginBtn.setText("Login Ulang");
                    logoutBtn.setVisible(true);
                    credentialsBtn.setVisible(false);
                } else {
                    accountStatusLabel.setText("Belum login ke Google Drive.");
                    loginBtn.setText("Login Google");
                    loginBtn.setDisable(false);
                    logoutBtn.setVisible(false);
                    credentialsBtn.setVisible(true);
                }
            });
        }, "drive-account-check");
        thread.setDaemon(true);
        thread.start();
    }

    private void doLogin() {
        loginBtn.setDisable(true);
        accountStatusLabel.setText("Membuka browser untuk login OAuth2...");
        appendLog("Membuka browser untuk otorisasi Google...");

        Thread thread = new Thread(() -> {
            try {
                driveService.authorize();
                String email = driveService.getUserAccountEmail();
                Platform.runLater(() -> {
                    accountStatusLabel.setText(email != null ? "Login: " + email : "Berhasil login!");
                    appendLog("Login berhasil.");
                    logoutBtn.setVisible(true);
                    loginBtn.setText("Login Ulang");
                    loginBtn.setDisable(false);
                });
            } catch (Exception e) {
                log.error("Login gagal", e);
                Platform.runLater(() -> {
                    accountStatusLabel.setText("Login gagal: " + e.getMessage());
                    appendLog("Gagal login: " + e.getMessage());
                    loginBtn.setDisable(false);
                });
            }
        }, "drive-login");
        thread.setDaemon(true);
        thread.start();
    }

    private void doLogout() {
        driveService.logout();
        refreshAccountStatusAsync();
        appendLog("Telah logout dari Google Drive.");
    }

    private void chooseCredentialsFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Pilih credentials.json (OAuth2 Client ID)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            try {
                driveService.saveCredentialsFile(file.toPath());
                appendLog("File credentials disimpan: " + file.getName());
                refreshAccountStatusAsync();
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Gagal menyimpan credentials: " + e.getMessage());
            }
        }
    }

    private void startUpload() {
        String activityName = activityField.getText().trim();
        if (activityName.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Silakan isi Nama Kegiatan.");
            activityField.requestFocus();
            return;
        }

        List<Path> filesToUpload = collectFilesToUpload();
        if (filesToUpload.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Tidak ada file yang dipilih untuk diunggah.");
            return;
        }

        LocalDate date = datePicker.getValue() != null ? datePicker.getValue() : LocalDate.now();
        String humasTarget = humasFolderIdField.getText().trim();
        driveService.setConfiguredHumasFolderId(humasTarget);

        // Kunci UI
        uploadButton.setDisable(true);
        cancelButton.setText("Batalkan Upload");
        activityField.setDisable(true);
        datePicker.setDisable(true);
        includeRawCheckBox.setDisable(true);
        humasFolderIdField.setDisable(true);
        loginBtn.setDisable(true);
        logoutBtn.setDisable(true);

        logArea.clear();
        appendLog("Memulai persiapan upload ke Google Drive...");

        currentUploadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Menghubungkan ke Google Drive...");
                Drive drive = driveService.getDriveService();

                // 1. Cari atau buat folder induk Humas
                appendLogAsync("Memeriksa folder induk Humas (" + humasTarget + ")...");
                String humasFolderId = driveService.getOrCreateHumasFolder(drive, humasTarget);
                appendLogAsync("Folder induk Humas siap: " + humasFolderId);

                if (isCancelled()) return null;

                // 2. Cari atau buat folder tahun (misal 2026) di bawah Humas
                int year = date.getYear();
                appendLogAsync("Mencari/membuat folder tahun " + year + " di bawah Humas...");
                String yearFolderId = driveService.getOrCreateYearFolder(drive, humasFolderId, year);
                appendLogAsync("Folder tahun siap: " + year + " (ID: " + yearFolderId + ")");

                if (isCancelled()) return null;

                // 3. Cari atau buat folder bulan (e.g. 10. Oktober) di bawah folder tahun
                int monthNum = date.getMonthValue();
                String monthName = GoogleDriveService.getMonthFolderName(monthNum);
                appendLogAsync("Mencari/membuat folder bulan " + monthName + " di bawah tahun " + year + "...");
                String monthFolderId = driveService.getOrCreateMonthFolder(drive, yearFolderId, monthNum);
                appendLogAsync("Folder bulan siap: " + monthName + " (ID: " + monthFolderId + ")");

                if (isCancelled()) return null;

                // 4. Buat folder kegiatan (YYMMDD - Kegiatan) di bawah folder bulan
                String actFolderName = GoogleDriveService.formatActivityFolderName(date, activityName);
                appendLogAsync("Mencari/membuat folder kegiatan: " + actFolderName + "...");
                String actFolderId = driveService.getOrCreateActivityFolder(drive, monthFolderId, date, activityName);
                appendLogAsync("Folder kegiatan siap: " + actFolderName + " (ID: " + actFolderId + ")");

                if (isCancelled()) return null;

                // 4. Upload file-file dengan Resumable Upload dan Retry
                int totalCount = filesToUpload.size();
                for (int i = 0; i < totalCount; i++) {
                    if (isCancelled()) {
                        appendLogAsync("Upload dibatalkan oleh pengguna.");
                        return null;
                    }

                    Path path = filesToUpload.get(i);
                    String fileName = path.getFileName().toString();
                    final int fileIndex = i + 1;

                    Platform.runLater(() -> {
                        totalProgressBar.setProgress((double) fileIndex / totalCount);
                        totalProgressLabel.setText(String.format("Total: File %d dari %d (%.0f%%)", 
                                fileIndex, totalCount, ((double) fileIndex / totalCount) * 100));
                        fileProgressBar.setProgress(0);
                        fileProgressLabel.setText("Mengunggah: " + fileName + " (0%)");
                    });

                    appendLogAsync(String.format("[%d/%d] Mengunggah: %s...", fileIndex, totalCount, fileName));

                    driveService.uploadFileWithRetry(
                            drive,
                            actFolderId,
                            path,
                            new GoogleDriveService.UploadProgressListener() {
                                @Override
                                public void onProgress(String name, long bytesUploaded, long totalBytes, double progressFraction) {
                                    Platform.runLater(() -> {
                                        fileProgressBar.setProgress(progressFraction);
                                        double mbUp = bytesUploaded / (1024.0 * 1024.0);
                                        double mbTot = totalBytes / (1024.0 * 1024.0);
                                        fileProgressLabel.setText(String.format("Mengunggah: %s (%.1f / %.1f MB - %.0f%%)", 
                                                name, mbUp, mbTot, progressFraction * 100));
                                    });
                                }

                                @Override
                                public void onStatusUpdate(String status) {
                                    appendLogAsync(status);
                                }
                            },
                            4 // Maksimal 4 kali retry
                    );

                    appendLogAsync(String.format("[%d/%d] Berhasil diunggah: %s", fileIndex, totalCount, fileName));
                }

                appendLogAsync("Semua file (" + totalCount + ") berhasil diunggah ke folder: " + actFolderName);
                return null;
            }
        };

        currentUploadTask.setOnSucceeded(e -> {
            fileProgressBar.setProgress(1.0);
            totalProgressBar.setProgress(1.0);
            fileProgressLabel.setText("Selesai!");
            totalProgressLabel.setText("Semua file berhasil diupload ke Google Drive!");

            showAlert(Alert.AlertType.INFORMATION, "Upload ke Google Drive berhasil selesai!\nFolder kegiatan: " 
                    + GoogleDriveService.formatActivityFolderName(date, activityName));

            if (onSuccess != null) {
                onSuccess.run();
            }
            stage.close();
        });

        currentUploadTask.setOnFailed(e -> {
            Throwable err = currentUploadTask.getException();
            log.error("Upload gagal", err);
            appendLog("ERROR: " + (err != null ? err.getMessage() : "Kesalahan tidak diketahui"));
            showAlert(Alert.AlertType.ERROR, "Gagal mengunggah file ke Google Drive:\n" + (err != null ? err.getMessage() : "Unknown error"));

            resetUiAfterTask();
        });

        currentUploadTask.setOnCancelled(e -> {
            appendLog("Upload dibatalkan.");
            resetUiAfterTask();
        });

        Thread thread = new Thread(currentUploadTask, "drive-upload-worker");
        thread.setDaemon(true);
        thread.start();
    }

    private void resetUiAfterTask() {
        uploadButton.setDisable(false);
        cancelButton.setText("Tutup");
        activityField.setDisable(false);
        datePicker.setDisable(false);
        includeRawCheckBox.setDisable(false);
        humasFolderIdField.setDisable(false);
        loginBtn.setDisable(false);
        logoutBtn.setDisable(false);
    }

    private void cancelOrClose() {
        if (currentUploadTask != null && currentUploadTask.isRunning()) {
            currentUploadTask.cancel();
            appendLog("Membatalkan proses upload...");
            cancelButton.setDisable(true);
        } else {
            stage.close();
        }
    }

    private void appendLog(String message) {
        logArea.appendText(message + "\n");
    }

    private void appendLogAsync(String message) {
        Platform.runLater(() -> appendLog(message));
    }

    private void showAlert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    public void show() {
        stage.showAndWait();
    }
}
