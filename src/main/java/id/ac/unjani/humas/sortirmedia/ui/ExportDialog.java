package id.ac.unjani.humas.sortirmedia.ui;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import id.ac.unjani.humas.sortirmedia.service.FileOperationService;
import id.ac.unjani.humas.sortirmedia.service.MediaGrouping;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ExportDialog {

    private final Stage stage = new Stage();
    private final List<MediaGrouping.MediaGroup> groups;
    private final Runnable onSuccess;

    private final Map<Integer, Path> flagPaths = new HashMap<>();
    private Path fallbackPath = null;

    private final RadioButton copyRadio = new RadioButton("Copy");
    private final RadioButton moveRadio = new RadioButton("Move");

    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label progressLabel = new Label("Menunggu...");

    private final Button startButton = new Button("Mulai");
    private final Button cancelButton = new Button("Batal");

    public ExportDialog(List<MediaGrouping.MediaGroup> groups, Runnable onSuccess) {
        this.groups = groups;
        this.onSuccess = onSuccess;

        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Export Media (" + groups.size() + " grup)");

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));

        // Mode pemilihan
        ToggleGroup modeGroup = new ToggleGroup();
        copyRadio.setToggleGroup(modeGroup);
        moveRadio.setToggleGroup(modeGroup);
        copyRadio.setSelected(true);
        HBox modeBox = new HBox(10, new Label("Mode Operasi:"), copyRadio, moveRadio);
        root.getChildren().add(modeBox);

        // Grid untuk mapping Flag -> Direktori
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        for (int i = 0; i < 5; i++) {
            final int flagIndex = i;
            Label flagLabel = new Label("Flag " + (i + 1) + " Tujuan:");
            TextField pathField = new TextField();
            pathField.setEditable(false);
            pathField.setPrefWidth(300);
            Button browseBtn = new Button("Browse...");

            browseBtn.setOnAction(e -> {
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("Pilih Folder untuk Flag " + (flagIndex + 1));
                File dir = chooser.showDialog(stage);
                if (dir != null) {
                    flagPaths.put(flagIndex, dir.toPath());
                    pathField.setText(dir.getAbsolutePath());
                }
            });

            grid.add(flagLabel, 0, i);
            grid.add(pathField, 1, i);
            grid.add(browseBtn, 2, i);
        }

        // Fallback untuk yang tidak diflag / flag tidak punya tujuan
        Label fallbackLabel = new Label("Lainnya Tujuan:");
        TextField fallbackField = new TextField();
        fallbackField.setEditable(false);
        fallbackField.setPrefWidth(300);
        Button fallbackBrowseBtn = new Button("Browse...");

        fallbackBrowseBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Pilih Folder Lainnya");
            File dir = chooser.showDialog(stage);
            if (dir != null) {
                fallbackPath = dir.toPath();
                fallbackField.setText(dir.getAbsolutePath());
            }
        });

        grid.add(fallbackLabel, 0, 5);
        grid.add(fallbackField, 1, 5);
        grid.add(fallbackBrowseBtn, 2, 5);

        root.getChildren().add(grid);

        progressBar.setPrefWidth(Double.MAX_VALUE);
        root.getChildren().addAll(progressBar, progressLabel);

        HBox buttonBox = new HBox(10, startButton, cancelButton);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        root.getChildren().add(buttonBox);

        startButton.setOnAction(e -> startOperation());
        cancelButton.setOnAction(e -> stage.close());

        Scene scene = new Scene(root);
        stage.setScene(scene);
    }

    public void show() {
        stage.showAndWait();
    }

    private void startOperation() {
        if (flagPaths.isEmpty() && fallbackPath == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Silakan pilih minimal satu folder tujuan.");
            alert.show();
            return;
        }

        startButton.setDisable(true);
        cancelButton.setDisable(true);
        copyRadio.setDisable(true);
        moveRadio.setDisable(true);

        FileOperationService.OperationMode mode = moveRadio.isSelected() ? 
                FileOperationService.OperationMode.MOVE : 
                FileOperationService.OperationMode.COPY;

        Task<Void> task = FileOperationService.createOperationTask(mode, groups, flagPaths, fallbackPath);

        progressBar.progressProperty().bind(task.progressProperty());
        progressLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(e -> {
            progressLabel.setText("Operasi selesai!");
            progressBar.progressProperty().unbind();
            progressBar.setProgress(1.0);
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Operasi " + mode.name() + " berhasil diselesaikan.");
            alert.showAndWait();
            
            if (onSuccess != null) {
                onSuccess.run();
            }
            stage.close();
        });

        task.setOnFailed(e -> {
            progressLabel.setText("Operasi gagal!");
            Alert alert = new Alert(Alert.AlertType.ERROR, "Terjadi kesalahan saat melakukan operasi.");
            alert.showAndWait();
            
            startButton.setDisable(false);
            cancelButton.setDisable(false);
        });

        Thread thread = new Thread(task, "file-operation");
        thread.setDaemon(true);
        thread.start();
    }
}
