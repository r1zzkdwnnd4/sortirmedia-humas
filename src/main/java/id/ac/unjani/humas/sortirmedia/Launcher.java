package id.ac.unjani.humas.sortirmedia;

import javafx.application.Application;

/**
 * Main class biasa (bukan turunan Application). Dipakai untuk menjalankan dari JAR/jpackage
 * nanti, karena JavaFX sering menolak start jika main class langsung turunan Application.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(SortirMediaApp.class, args);
    }
}
