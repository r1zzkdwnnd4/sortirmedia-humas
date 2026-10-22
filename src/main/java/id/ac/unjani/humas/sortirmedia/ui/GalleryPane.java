package id.ac.unjani.humas.sortirmedia.ui;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import id.ac.unjani.humas.sortirmedia.service.PreviewService;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/**
 * Galeri thumbnail: menampilkan folder dan kartu pratinjau file media.
 * Setiap thumbnail dimuat secara asinkron di background thread melalui PreviewService.
 */
public final class GalleryPane {

    private static final int CELL_SIZE = PreviewService.THUMBNAIL_SIZE + 20;

    private final TilePane tilePane = new TilePane();
    private final ScrollPane scrollPane = new ScrollPane(tilePane);
    private final PreviewService previewService = new PreviewService();

    private List<Path> currentSubfolders = Collections.emptyList();
    private Consumer<MediaItem> onItemSelected = item -> {};
    private Consumer<MediaItem> onItemAction = null;
    private Consumer<MediaItem> onFlagChanged = null;
    private Consumer<Path> onFolderAction = null;
    private Consumer<Path> onFolderSelected = null;

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

    public void setOnFolderAction(Consumer<Path> callback) {
        this.onFolderAction = callback;
    }

    public void setOnFolderSelected(Consumer<Path> callback) {
        this.onFolderSelected = callback;
    }

    public void setSubfolders(List<Path> subfolders) {
        this.currentSubfolders = subfolders != null ? subfolders : Collections.emptyList();
    }

    public void showItems(List<MediaItem> items) {
        showItems(this.currentSubfolders, items);
    }

    public void showItems(List<Path> subfolders, List<MediaItem> items) {
        this.currentSubfolders = subfolders != null ? subfolders : Collections.emptyList();
        tilePane.getChildren().clear();
        for (Path subfolder : this.currentSubfolders) {
            tilePane.getChildren().add(buildFolderCell(subfolder));
        }
        for (MediaItem item : items) {
            tilePane.getChildren().add(buildCell(item));
        }
    }

    public List<MediaItem> getVisibleMediaItems() {
        return tilePane.getChildren().stream()
                .map(javafx.scene.Node::getUserData)
                .filter(data -> data instanceof MediaItem)
                .map(data -> (MediaItem) data)
                .toList();
    }

    public void clear() {
        currentSubfolders = Collections.emptyList();
        tilePane.getChildren().clear();
    }

    private StackPane buildFolderCell(Path folder) {
        SVGPath folderIcon = new SVGPath();
        folderIcon.setContent("M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z");
        folderIcon.setFill(Color.web("#FFB300"));
        folderIcon.setScaleX(2.8);
        folderIcon.setScaleY(2.8);

        Label typeBadge = new Label("FOLDER");
        typeBadge.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #E65100; "
                + "-fx-background-color: #FFE082; -fx-padding: 1 4 1 4; -fx-background-radius: 3;");
        StackPane.setAlignment(typeBadge, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(typeBadge, new Insets(4));

        StackPane iconHolder = new StackPane(folderIcon, typeBadge);
        iconHolder.setPrefSize(PreviewService.THUMBNAIL_SIZE, PreviewService.THUMBNAIL_SIZE);
        iconHolder.setStyle("-fx-background-color: -fx-control-inner-background; "
                + "-fx-border-color: -fx-box-border; -fx-border-radius: 4; -fx-background-radius: 4;");

        String name = folder.getFileName() != null ? folder.getFileName().toString() : folder.toString();
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        nameLabel.setMaxWidth(PreviewService.THUMBNAIL_SIZE);
        nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        VBox cell = new VBox(4, iconHolder, nameLabel);
        cell.setAlignment(Pos.CENTER);
        cell.setPrefWidth(CELL_SIZE);
        cell.setFocusTraversable(true);
        cell.setCursor(Cursor.HAND);

        cell.setOnMouseClicked(e -> {
            cell.requestFocus();
            if (onFolderSelected != null) {
                onFolderSelected.accept(folder);
            }
            if (e.getClickCount() == 2 && onFolderAction != null) {
                onFolderAction.accept(folder);
            }
        });

        cell.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER && onFolderAction != null) {
                onFolderAction.accept(folder);
            }
        });

        cell.setOnMouseEntered(e -> iconHolder.setStyle("-fx-background-color: #E3F2FD; "
                + "-fx-border-color: #2196F3; -fx-border-radius: 4; -fx-background-radius: 4;"));
        cell.setOnMouseExited(e -> iconHolder.setStyle("-fx-background-color: -fx-control-inner-background; "
                + "-fx-border-color: -fx-box-border; -fx-border-radius: 4; -fx-background-radius: 4;"));

        StackPane container = new StackPane(cell);
        container.setUserData(folder);
        return container;
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