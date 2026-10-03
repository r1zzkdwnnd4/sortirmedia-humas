package id.ac.unjani.humas.sortirmedia;

import id.ac.unjani.humas.sortirmedia.ui.MainView;
import id.ac.unjani.humas.sortirmedia.util.AppInfo;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point JavaFX. Fase 0: hanya jendela kosong untuk memastikan fondasi berjalan. */
public class SortirMediaApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(SortirMediaApp.class);

    @Override
    public void start(Stage stage) {
        MainView mainView = new MainView();

        stage.setTitle(AppInfo.NAME);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(new Scene(mainView.getView(), 1200, 750));
        stage.show();

        log.info("{} v{} dimulai (Java {}, JavaFX {})",
                AppInfo.NAME,
                AppInfo.VERSION,
                System.getProperty("java.version"),
                System.getProperty("javafx.runtime.version"));
    }
}
