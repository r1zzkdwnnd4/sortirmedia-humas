package id.ac.unjani.humas.sortirmedia.ui;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.layout.StackPane;

public class BadgeBuilder {
    private static final String[] COLORS = {
        "#ff4444", // 1 - Red
        "#ffbb33", // 2 - Orange
        "#00C851", // 3 - Green
        "#33b5e5", // 4 - Blue
        "#aa66cc"  // 5 - Purple
    };

    public static HBox createBadges(MediaItem item) {
        HBox box = new HBox(4);
        box.setAlignment(Pos.TOP_RIGHT);
        box.setPadding(new Insets(4));
        box.setMouseTransparent(true);
        updateBadges(box, item);
        return box;
    }

    public static void updateBadges(HBox box, MediaItem item) {
        box.getChildren().clear();
        for (int i = 0; i < 5; i++) {
            if (item.hasFlag(i)) {
                StackPane badge = new StackPane();
                Circle circle = new Circle(8, Color.web(COLORS[i]));
                Label label = new Label(String.valueOf(i + 1));
                label.setStyle("-fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;");
                badge.getChildren().addAll(circle, label);
                box.getChildren().add(badge);
            }
        }
    }
}
