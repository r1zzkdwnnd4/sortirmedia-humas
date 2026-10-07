package id.ac.unjani.humas.sortirmedia.ui;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import id.ac.unjani.humas.sortirmedia.service.PreviewService;
import java.util.List;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;

/**
 * Viewer fullscreen. Menampilkan foto secara penuh, mendukung zoom dan pan.
 * Mem-preload gambar berikutnya agar perpindahan instan (AGENTS.md Bagian 6.5).
 */
public final class ViewerPane {

    private final StackPane root = new StackPane();
    private final ImageView imageView = new ImageView();
    private final javafx.scene.layout.HBox badgeBox = new javafx.scene.layout.HBox();
    private final PreviewService previewService = new PreviewService();

    private List<MediaItem> items;
    private int currentIndex = -1;
    
    private Consumer<MediaItem> onClose;
    private Consumer<MediaItem> onItemChanged;
    private Consumer<MediaItem> onFlagChanged;

    private double dragStartX, dragStartY;
    private double imgTranslateStartX, imgTranslateStartY;

    public ViewerPane() {
        root.setStyle("-fx-background-color: black;");
        root.setAlignment(Pos.CENTER);
        
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        
        badgeBox.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        badgeBox.setPadding(new javafx.geometry.Insets(20));
        badgeBox.setSpacing(10);
        badgeBox.setMouseTransparent(true);
        
        root.getChildren().addAll(imageView, badgeBox);

        // Event listener
        root.setFocusTraversable(true);
        root.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyPress);
        root.addEventHandler(ScrollEvent.SCROLL, this::handleScroll);
        
        root.addEventHandler(MouseEvent.MOUSE_PRESSED, this::handleMousePressed);
        root.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::handleMouseDragged);
        root.addEventHandler(MouseEvent.MOUSE_CLICKED, this::handleMouseClicked);
    }

    public StackPane getView() {
        return root;
    }

    public void setOnClose(Consumer<MediaItem> onClose) {
        this.onClose = onClose;
    }
    
    public void setOnItemChanged(Consumer<MediaItem> onItemChanged) {
        this.onItemChanged = onItemChanged;
    }
    
    public void setOnFlagChanged(Consumer<MediaItem> onFlagChanged) {
        this.onFlagChanged = onFlagChanged;
    }

    public void open(List<MediaItem> items, int startIndex) {
        this.items = items;
        this.currentIndex = startIndex;
        loadCurrentImage();
        Platform.runLater(root::requestFocus);
    }

    private void handleKeyPress(KeyEvent e) {
        if (e.getCode() == KeyCode.ESCAPE) {
            close();
        } else if (e.getCode() == KeyCode.LEFT) {
            navigate(-1);
        } else if (e.getCode() == KeyCode.RIGHT) {
            navigate(1);
        } else if (items != null && currentIndex >= 0 && currentIndex < items.size()) {
            MediaItem item = items.get(currentIndex);
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
        e.consume();
    }

    private void navigate(int direction) {
        if (items == null || items.isEmpty()) return;
        int newIndex = currentIndex + direction;
        if (newIndex >= 0 && newIndex < items.size()) {
            currentIndex = newIndex;
            loadCurrentImage();
        }
    }

    private void loadCurrentImage() {
        if (currentIndex < 0 || currentIndex >= items.size()) return;
        
        MediaItem item = items.get(currentIndex);
        if (onItemChanged != null) onItemChanged.accept(item);
        
        BadgeBuilder.updateBadges(badgeBox, item);
        
        // Reset transform
        imageView.setTranslateX(0);
        imageView.setTranslateY(0);
        imageView.setScaleX(1.0);
        imageView.setScaleY(1.0);
        imageView.setFitWidth(root.getWidth());
        imageView.setFitHeight(root.getHeight());

        Task<Image> task = new Task<>() {
            @Override
            protected Image call() {
                // Request size 0 means original size or full preview. We use 2048 to limit memory but keep it large
                return previewService.getPreview(item, 2048);
            }
        };
        task.setOnSucceeded(e -> {
            Image img = task.getValue();
            if (img != null) {
                imageView.setImage(img);
                // Bind fit width/height to root when scale is 1
                fitToScreen();
                preloadNextImages();
            }
        });
        Thread t = new Thread(task, "viewer-load");
        t.setDaemon(true);
        t.start();
    }
    
    private void preloadNextImages() {
        int next = currentIndex + 1;
        int prev = currentIndex - 1;
        
        if (next < items.size()) {
            MediaItem nextItem = items.get(next);
            Thread t = new Thread(() -> previewService.getPreview(nextItem, 2048), "preload-next");
            t.setDaemon(true); t.start();
        }
        if (prev >= 0) {
            MediaItem prevItem = items.get(prev);
            Thread t = new Thread(() -> previewService.getPreview(prevItem, 2048), "preload-prev");
            t.setDaemon(true); t.start();
        }
    }

    private void handleScroll(ScrollEvent e) {
        if (imageView.getImage() == null) return;
        
        double zoomFactor = 1.1;
        double deltaY = e.getDeltaY();
        
        if (deltaY < 0) {
            zoomFactor = 1 / zoomFactor;
        }

        // Apply scale
        double oldScale = imageView.getScaleX();
        double newScale = oldScale * zoomFactor;
        
        if (newScale < 1.0) newScale = 1.0;
        if (newScale > 10.0) newScale = 10.0;
        
        if (newScale == 1.0) {
            fitToScreen();
        } else {
            imageView.setFitWidth(imageView.getImage().getWidth());
            imageView.setFitHeight(imageView.getImage().getHeight());
            imageView.setScaleX(newScale);
            imageView.setScaleY(newScale);
        }
        e.consume();
    }
    
    private void fitToScreen() {
        imageView.setScaleX(1.0);
        imageView.setScaleY(1.0);
        imageView.setTranslateX(0);
        imageView.setTranslateY(0);
        if (root.getWidth() > 0) imageView.setFitWidth(root.getWidth());
        if (root.getHeight() > 0) imageView.setFitHeight(root.getHeight());
    }

    private void handleMousePressed(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY) {
            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();
            imgTranslateStartX = imageView.getTranslateX();
            imgTranslateStartY = imageView.getTranslateY();
        }
    }

    private void handleMouseDragged(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY && imageView.getScaleX() > 1.0) {
            double deltaX = e.getSceneX() - dragStartX;
            double deltaY = e.getSceneY() - dragStartY;
            imageView.setTranslateX(imgTranslateStartX + deltaX);
            imageView.setTranslateY(imgTranslateStartY + deltaY);
        }
    }

    private void handleMouseClicked(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
            if (imageView.getScaleX() > 1.0) {
                fitToScreen();
            } else {
                imageView.setFitWidth(imageView.getImage().getWidth());
                imageView.setFitHeight(imageView.getImage().getHeight());
                imageView.setScaleX(1.0);
                imageView.setScaleY(1.0);
            }
        }
    }

    private void close() {
        if (onClose != null && currentIndex >= 0 && currentIndex < items.size()) {
            onClose.accept(items.get(currentIndex));
        }
    }
}
