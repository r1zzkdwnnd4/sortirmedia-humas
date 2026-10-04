package id.ac.unjani.humas.sortirmedia.ui;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import id.ac.unjani.humas.sortirmedia.service.PreviewService;
import java.util.List;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Galeri thumbnail. Tiap thumbnail dimuat di background Task lewat
 * PreviewService, yang
 * otomatis memilih cara baca sesuai jenis file (foto langsung, RAW lewat
 * exiftool, video
 * lewat ffmpeg) tanpa GalleryPane perlu tahu detailnya (AGENTS.md Bagian 6.3).
 */
public final class GalleryPane {

    private static final int CELL_SIZE = PreviewService.THUMBNAIL_SIZE + 20;

    private final TilePane tilePane = new TilePane();
    private final ScrollPane scrollPane = new ScrollPane(tilePane);
    private final PreviewService previewService = new PreviewService();

    private Consumer<MediaItem> onItemSelected = item -> {
    };

    public GalleryPane() {
        tilePane.setPadding(new Insets(10));
        tilePane.setHgap(10);
        tilePane.setVgap(10);
        tilePane.setPrefColumns(6);
        scrollPane.setFitToWidth(true);
        scrollPane.setContent(tilePane);
    }

    public ScrollPane getView() {
        return scrollPane;
    }

    public void setOnItemSelected(Consumer<MediaItem> callback) {
        this.onItemSelected = callback;
    }

    public void showItems(List<MediaItem> items) {
        tilePane.getChildren().clear();
        for (MediaItem item : items)
            tilePane.getChildren().add(buildCell(item));
    }

    public void clear() {
        tilePane.getChildren().clear();
    }

    private StackPane buildCell(MediaItem item) {
        ImageView imageView = new ImageView();
        imageView.setFitWidth(PreviewService.THUMBNAIL_SIZE);
        imageView.setFitHeight(PreviewService.THUMBNAIL_SIZE);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        Label placeholder = new Label(placeholderText(item));
        placeholder.setWrapText(true);
        placeholder.setTextFill(Color.GRAY);
        placeholder.setStyle("-fx-font-size: 11px;");

        StackPane imageHolder = new StackPane(placeholder, imageView);
        imageHolder.setPrefSize(PreviewService.THUMBNAIL_SIZE, PreviewService.THUMBNAIL_SIZE);
        imageHolder.setStyle("-fx-background-color: -fx-control-inner-background; "
                + "-fx-border-color: -fx-box-border; -fx-border-radius: 4; -fx-background-radius: 4;");

        Label nameLabel = new Label(item.getFileName());
        nameLabel.setStyle("-fx-font-size: 11px;");
        nameLabel.setMaxWidth(PreviewService.THUMBNAIL_SIZE);

        VBox cell = new VBox(4, imageHolder, nameLabel);
        cell.setAlignment(Pos.CENTER);
        cell.setPrefWidth(CELL_SIZE);
        cell.setOnMouseClicked(e -> onItemSelected.accept(item));
        cell.setCursor(javafx.scene.Cursor.HAND);

        loadThumbnailAsync(item, imageView, placeholder);
        return new StackPane(cell);
    }

    private void loadThumbnailAsync(MediaItem item, ImageView imageView, Label placeholder) {
        Task<Image> task = new Task<>() {
            @Override
            protected Image call() {
                // Baca metadata dulu (resolusi/durasi) supaya saat item ini diklik dan status
                // bar menampilkan info file, datanya sudah siap.
                previewService.readMetadata(item);
                return previewService.getThumbnail(item);
            }
        };
        task.setOnSucceeded(e -> {
            Image image = task.getValue();
            if (image != null) {
                imageView.setImage(image);
                placeholder.setVisible(false);
            }
        });
        Thread thread = new Thread(task, "preview-" + item.getFileName());
        thread.setDaemon(true);
        thread.start();
    }

    private String placeholderText(MediaItem item) {
        return switch (item.getType()) {
            case RAW -> "RAW";
            case VIDEO -> "VIDEO";
            default -> "...";
        };
    }
}