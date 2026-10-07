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
    private Consumer<MediaItem> onItemAction = null;
    private Consumer<MediaItem> onFlagChanged = null;

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
    
    public void setOnItemAction(Consumer<MediaItem> callback) {
        this.onItemAction = callback;
    }
    
    public void setOnFlagChanged(Consumer<MediaItem> callback) {
        this.onFlagChanged = callback;
    }

    public void showItems(List<MediaItem> items) {
        tilePane.getChildren().clear();
        for (MediaItem item : items)
            tilePane.getChildren().add(buildCell(item));
    }

    public void clear() {
        tilePane.getChildren().clear();
    }

    public void scrollTo(MediaItem item) {
        int index = -1;
        for (int i = 0; i < tilePane.getChildren().size(); i++) {
            if (tilePane.getChildren().get(i).getUserData() == item) {
                index = i;
                break;
            }
        }
        if (index >= 0) {
            double vValue = (double) index / tilePane.getChildren().size();
            scrollPane.setVvalue(vValue);
            tilePane.getChildren().get(index).requestFocus();
        }
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

        javafx.scene.layout.HBox badgeBox = BadgeBuilder.createBadges(item);

        StackPane imageHolder = new StackPane(placeholder, imageView, badgeBox);
        imageHolder.setPrefSize(PreviewService.THUMBNAIL_SIZE, PreviewService.THUMBNAIL_SIZE);
        imageHolder.setStyle("-fx-background-color: -fx-control-inner-background; "
                + "-fx-border-color: -fx-box-border; -fx-border-radius: 4; -fx-background-radius: 4;");

        Label nameLabel = new Label(item.getFileName());
        nameLabel.setStyle("-fx-font-size: 11px;");
        nameLabel.setMaxWidth(PreviewService.THUMBNAIL_SIZE);

        VBox cell = new VBox(4, imageHolder, nameLabel);
        cell.setAlignment(Pos.CENTER);
        cell.setPrefWidth(CELL_SIZE);
        cell.setOnMouseClicked(e -> {
            onItemSelected.accept(item);
            if (e.getClickCount() == 2 && onItemAction != null) {
                onItemAction.accept(item);
            }
        });
        cell.setFocusTraversable(true);
        cell.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER && onItemAction != null) {
                onItemAction.accept(item);
            } else {
                boolean changed = true;
                switch (e.getCode()) {
                    case DIGIT0, NUMPAD0 -> item.clearFlags();
                    case DIGIT1, NUMPAD1 -> item.toggleFlag(0);
                    case DIGIT2, NUMPAD2 -> item.toggleFlag(1);
                    case DIGIT3, NUMPAD3 -> item.toggleFlag(2);
                    case DIGIT4, NUMPAD4 -> item.toggleFlag(3);
                    case DIGIT5, NUMPAD5 -> item.toggleFlag(4);
                    default -> changed = false;
                }
                if (changed) {
                    BadgeBuilder.updateBadges(badgeBox, item);
                    if (onFlagChanged != null) {
                        onFlagChanged.accept(item);
                    }
                }
            }
        });
        cell.setCursor(javafx.scene.Cursor.HAND);
        cell.setUserData(item);

        loadThumbnailAsync(item, imageView, placeholder);
        StackPane container = new StackPane(cell);
        container.setUserData(item);
        return container;
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