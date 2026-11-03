package id.ac.unjani.humas.sortirmedia.ui;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import id.ac.unjani.humas.sortirmedia.service.PreviewService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;

/**
 * Galeri thumbnail: menampilkan folder dan kartu pratinjau file media.
 * Dilengkapi dengan fitur multi-seleksi (file & folder), highlight visual,
 * badge centang, shortcut keyboard, dan pemuatan asinkron thumbnail.
 */
public final class GalleryPane {

    private static final int CELL_SIZE = PreviewService.THUMBNAIL_SIZE + 20;

    private static final String NORMAL_HOLDER_STYLE =
            "-fx-background-color: -fx-control-inner-background; -fx-border-color: -fx-box-border; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;";
    private static final String HOVER_HOLDER_STYLE =
            "-fx-background-color: #F0F7FF; -fx-border-color: #90CAF9; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;";
    private static final String SELECTED_HOLDER_STYLE =
            "-fx-background-color: #E3F2FD; -fx-border-color: #1976D2; -fx-border-width: 2.5; -fx-border-radius: 6; -fx-background-radius: 6;";

    private final TilePane tilePane = new TilePane();
    private final ScrollPane scrollPane = new ScrollPane(tilePane);
    private final PreviewService previewService = new PreviewService();

    private List<Path> currentSubfolders = Collections.emptyList();
    private final Set<Object> selectedItems = new LinkedHashSet<>();
    private Object lastFocusedItem = null;

    private Consumer<MediaItem> onItemSelected = item -> {};
    private Consumer<MediaItem> onItemAction = null;
    private Consumer<MediaItem> onFlagChanged = null;
    private Consumer<Path> onFolderAction = null;
    private Consumer<Path> onFolderSelected = null;
    private Runnable onSelectionChanged = null;

    public GalleryPane() {
        tilePane.setPadding(new Insets(10));
        tilePane.setHgap(10);
        tilePane.setVgap(10);
        tilePane.setPrefColumns(6);
        scrollPane.setFitToWidth(true);
        scrollPane.setContent(tilePane);

        // Klik pada area kosong galeri untuk membatalkan seleksi
        tilePane.setOnMouseClicked(e -> {
            if (e.getTarget() == tilePane && e.getButton() == MouseButton.PRIMARY) {
                deselectAll();
            }
        });
        scrollPane.setOnMouseClicked(e -> {
            if (e.getTarget() == scrollPane && e.getButton() == MouseButton.PRIMARY) {
                deselectAll();
            }
        });
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

    public void setOnSelectionChanged(Runnable callback) {
        this.onSelectionChanged = callback;
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

        // Pertahankan item terseleksi yang masih ada/tampil saat ini
        Set<Object> currentSet = new HashSet<>(this.currentSubfolders);
        currentSet.addAll(items);
        selectedItems.retainAll(currentSet);

        for (Path subfolder : this.currentSubfolders) {
            tilePane.getChildren().add(buildFolderCell(subfolder));
        }
        for (MediaItem item : items) {
            tilePane.getChildren().add(buildCell(item));
        }

        refreshSelectionStyles();
    }

    public List<MediaItem> getVisibleMediaItems() {
        return tilePane.getChildren().stream()
                .map(Node::getUserData)
                .filter(data -> data instanceof MediaItem)
                .map(data -> (MediaItem) data)
                .toList();
    }

    public List<Object> getVisibleItemsAndFolders() {
        List<Object> list = new ArrayList<>();
        for (Node node : tilePane.getChildren()) {
            if (node.getUserData() != null) {
                list.add(node.getUserData());
            }
        }
        return list;
    }

    public void clear() {
        currentSubfolders = Collections.emptyList();
        selectedItems.clear();
        lastFocusedItem = null;
        tilePane.getChildren().clear();
        if (onSelectionChanged != null) {
            onSelectionChanged.run();
        }
    }

    // ==========================================
    // LOGIKA SELEKSI (Select All, Deselect, etc)
    // ==========================================

    public void selectAll() {
        for (Node node : tilePane.getChildren()) {
            if (node.getUserData() != null) {
                selectedItems.add(node.getUserData());
            }
        }
        refreshSelectionStyles();
    }

    public void deselectAll() {
        selectedItems.clear();
        lastFocusedItem = null;
        refreshSelectionStyles();
    }

    public void selectSingle(Object item) {
        selectedItems.clear();
        selectedItems.add(item);
        lastFocusedItem = item;
        refreshSelectionStyles();
    }

    public void toggleSelection(Object item) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item);
        } else {
            selectedItems.add(item);
            lastFocusedItem = item;
        }
        refreshSelectionStyles();
    }

    public void selectRange(Object fromItem, Object toItem) {
        List<Object> allVisible = getVisibleItemsAndFolders();
        int idx1 = allVisible.indexOf(fromItem);
        int idx2 = allVisible.indexOf(toItem);
        if (idx1 < 0 || idx2 < 0) {
            selectSingle(toItem);
            return;
        }
        int start = Math.min(idx1, idx2);
        int end = Math.max(idx1, idx2);
        for (int i = start; i <= end; i++) {
            selectedItems.add(allVisible.get(i));
        }
        lastFocusedItem = toItem;
        refreshSelectionStyles();
    }

    public Set<Object> getSelectedItems() {
        return Collections.unmodifiableSet(selectedItems);
    }

    public List<MediaItem> getSelectedMediaItems() {
        return selectedItems.stream()
                .filter(o -> o instanceof MediaItem)
                .map(o -> (MediaItem) o)
                .toList();
    }

    public List<Path> getSelectedFolders() {
        return selectedItems.stream()
                .filter(o -> o instanceof Path)
                .map(o -> (Path) o)
                .toList();
    }

    public void refreshSelectionStyles() {
        for (Node node : tilePane.getChildren()) {
            if (node instanceof StackPane container) {
                boolean isSelected = selectedItems.contains(container.getUserData());
                applySelectionStyle(container, isSelected);
            }
        }
        if (onSelectionChanged != null) {
            onSelectionChanged.run();
        }
    }

    private void applySelectionStyle(StackPane container, boolean isSelected) {
        Region holder = (Region) container.getProperties().get("holder");
        Node checkBadge = (Node) container.getProperties().get("checkBadge");
        if (holder != null) {
            holder.setStyle(isSelected ? SELECTED_HOLDER_STYLE : NORMAL_HOLDER_STYLE);
        }
        if (checkBadge != null) {
            checkBadge.setVisible(isSelected);
        }
    }

    private void handleItemClick(Object item, MouseEvent e) {
        if (e.isControlDown() || e.isShortcutDown()) {
            toggleSelection(item);
        } else if (e.isShiftDown() && lastFocusedItem != null) {
            selectRange(lastFocusedItem, item);
        } else if (e.getX() <= 36 && e.getY() <= 36) {
            // Klik pada atau di sekitar lingkaran badge centang (top-left)
            toggleSelection(item);
        } else {
            selectSingle(item);
        }
    }

    private StackPane createCheckBadge() {
        Circle circle = new Circle(10, Color.web("#1976D2"));
        circle.setStroke(Color.WHITE);
        circle.setStrokeWidth(1.5);

        SVGPath check = new SVGPath();
        check.setContent("M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z");
        check.setFill(Color.WHITE);
        check.setScaleX(0.7);
        check.setScaleY(0.7);

        StackPane badge = new StackPane(circle, check);
        badge.setAlignment(Pos.CENTER);
        StackPane.setAlignment(badge, Pos.TOP_LEFT);
        StackPane.setMargin(badge, new Insets(6));
        badge.setMouseTransparent(true);
        badge.setVisible(false);
        return badge;
    }

    // ==========================================
    // CELL BUILDERS
    // ==========================================

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

        StackPane checkBadge = createCheckBadge();

        StackPane iconHolder = new StackPane(folderIcon, typeBadge, checkBadge);
        iconHolder.setPrefSize(PreviewService.THUMBNAIL_SIZE, PreviewService.THUMBNAIL_SIZE);
        iconHolder.setStyle(NORMAL_HOLDER_STYLE);

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
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            if (e.getClickCount() == 2 && onFolderAction != null) {
                onFolderAction.accept(folder);
                return;
            }
            handleItemClick(folder, e);
            if (onFolderSelected != null) {
                onFolderSelected.accept(folder);
            }
        });

        cell.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && onFolderAction != null) {
                onFolderAction.accept(folder);
            } else if (e.getCode() == KeyCode.SPACE) {
                toggleSelection(folder);
                e.consume();
            }
        });

        cell.setOnMouseEntered(e -> {
            if (!selectedItems.contains(folder)) {
                iconHolder.setStyle(HOVER_HOLDER_STYLE);
            }
        });
        cell.setOnMouseExited(e -> {
            if (selectedItems.contains(folder)) {
                iconHolder.setStyle(SELECTED_HOLDER_STYLE);
            } else {
                iconHolder.setStyle(NORMAL_HOLDER_STYLE);
            }
        });

        StackPane container = new StackPane(cell);
        container.setUserData(folder);
        container.getProperties().put("holder", iconHolder);
        container.getProperties().put("checkBadge", checkBadge);
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

        HBox badgeBox = BadgeBuilder.createBadges(item);
        StackPane checkBadge = createCheckBadge();

        StackPane imageHolder = new StackPane(placeholder, imageView, badgeBox, checkBadge);
        imageHolder.setPrefSize(PreviewService.THUMBNAIL_SIZE, PreviewService.THUMBNAIL_SIZE);
        imageHolder.setStyle(NORMAL_HOLDER_STYLE);

        Label nameLabel = new Label(item.getFileName());
        nameLabel.setStyle("-fx-font-size: 11px;");
        nameLabel.setMaxWidth(PreviewService.THUMBNAIL_SIZE);

        VBox cell = new VBox(4, imageHolder, nameLabel);
        cell.setAlignment(Pos.CENTER);
        cell.setPrefWidth(CELL_SIZE);
        cell.setCursor(Cursor.HAND);
        cell.setFocusTraversable(true);

        cell.setOnMouseClicked(e -> {
            cell.requestFocus();
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            if (e.getClickCount() == 2 && onItemAction != null) {
                onItemAction.accept(item);
                return;
            }
            handleItemClick(item, e);
            if (onItemSelected != null) {
                onItemSelected.accept(item);
            }
        });

        cell.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && onItemAction != null) {
                onItemAction.accept(item);
            } else if (e.getCode() == KeyCode.SPACE) {
                toggleSelection(item);
                e.consume();
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

        cell.setOnMouseEntered(e -> {
            if (!selectedItems.contains(item)) {
                imageHolder.setStyle(HOVER_HOLDER_STYLE);
            }
        });
        cell.setOnMouseExited(e -> {
            if (selectedItems.contains(item)) {
                imageHolder.setStyle(SELECTED_HOLDER_STYLE);
            } else {
                imageHolder.setStyle(NORMAL_HOLDER_STYLE);
            }
        });

        cell.setUserData(item);
        loadThumbnailAsync(item, imageView, placeholder);

        StackPane container = new StackPane(cell);
        container.setUserData(item);
        container.getProperties().put("holder", imageHolder);
        container.getProperties().put("checkBadge", checkBadge);
        return container;
    }

    private void loadThumbnailAsync(MediaItem item, ImageView imageView, Label placeholder) {
        Task<Image> task = new Task<>() {
            @Override
            protected Image call() {
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
