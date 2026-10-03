package id.ac.unjani.humas.sortirmedia.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Panel folder di kiri: akses cepat (Pictures/Desktop/Downloads) + daftar drive, dengan
 * lazy loading (anak folder baru dibaca saat node di-expand) sesuai AGENTS.md Bagian 7.
 * Hanya menampilkan FOLDER, tidak menampilkan file di dalamnya.
 */
public final class FolderTreePane {

    private static final Logger log = LoggerFactory.getLogger(FolderTreePane.class);

    private final TreeView<FolderEntry> treeView = new TreeView<>();
    private Consumer<Path> onFolderSelected = p -> {};

    public FolderTreePane() {
        TreeItem<FolderEntry> root = new TreeItem<>(new FolderEntry("Root", null));
        root.setExpanded(true);
        treeView.setRoot(root);
        treeView.setShowRoot(false);
        treeView.setCellFactory(tv -> new FolderCell());

        root.getChildren().add(buildQuickAccessSection());
        root.getChildren().add(buildDriveSection());

        treeView.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null && sel.getValue().path() != null) {
                onFolderSelected.accept(sel.getValue().path());
            }
        });
    }

    public TreeView<FolderEntry> getView() { return treeView; }
    public void setOnFolderSelected(Consumer<Path> callback) { this.onFolderSelected = callback; }

    /** Memaksa ulang isi daftar drive, mis. setelah flashdisk baru dicolokkan. */
    public void refreshDrives() {
        TreeItem<FolderEntry> root = treeView.getRoot();
        root.getChildren().removeIf(item -> "Drive".equals(item.getValue().label()));
        root.getChildren().add(buildDriveSection());
    }

    private TreeItem<FolderEntry> buildQuickAccessSection() {
        TreeItem<FolderEntry> section = new TreeItem<>(new FolderEntry("Akses Cepat", null));
        section.setExpanded(true);
        addIfExists(section, "Pictures", userHome("Pictures"));
        addIfExists(section, "Desktop", userHome("Desktop"));
        addIfExists(section, "Downloads", userHome("Downloads"));
        return section;
    }

    private TreeItem<FolderEntry> buildDriveSection() {
        TreeItem<FolderEntry> section = new TreeItem<>(new FolderEntry("Drive", null));
        section.setExpanded(true);
        for (File root : File.listRoots()) {
            Path path = root.toPath();
            section.getChildren().add(lazyFolderItem(root.getAbsolutePath(), path));
        }
        return section;
    }

    private void addIfExists(TreeItem<FolderEntry> parent, String label, Path path) {
        if (path != null && Files.isDirectory(path)) {
            parent.getChildren().add(lazyFolderItem(label, path));
        }
    }

    private Path userHome(String sub) {
        return Path.of(System.getProperty("user.home"), sub);
    }

    /** Membuat TreeItem dengan satu child dummy, supaya panah expand muncul tanpa membaca isi dulu. */
    private TreeItem<FolderEntry> lazyFolderItem(String label, Path path) {
        TreeItem<FolderEntry> item = new TreeItem<>(new FolderEntry(label, path));
        item.getChildren().add(new TreeItem<>(DUMMY));
        item.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
            if (isExpanded && isDummyOnly(item)) {
                loadChildren(item, path);
            }
        });
        return item;
    }

    private boolean isDummyOnly(TreeItem<FolderEntry> item) {
        return item.getChildren().size() == 1 && item.getChildren().get(0).getValue() == DUMMY;
    }

    private void loadChildren(TreeItem<FolderEntry> item, Path path) {
        List<TreeItem<FolderEntry>> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, Files::isDirectory)) {
            List<Path> subfolders = new ArrayList<>();
            for (Path p : stream) {
                if (!Files.isHidden(p)) subfolders.add(p);
            }
            subfolders.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));
            for (Path sub : subfolders) {
                children.add(lazyFolderItem(sub.getFileName().toString(), sub));
            }
        } catch (IOException e) {
            // Folder terkunci/tanpa izin: dilewati saja, tidak melempar error ke pengguna.
            log.debug("Tidak bisa membaca subfolder dari {}", path, e);
        }
        item.getChildren().setAll(children);
    }

    private static final FolderEntry DUMMY = new FolderEntry("__dummy__", null);

    /** Entri pohon folder: label tampilan + path (null untuk node kategori seperti "Drive"). */
    public record FolderEntry(String label, Path path) {
        @Override
        public String toString() { return label; }
    }

    private static final class FolderCell extends javafx.scene.control.TreeCell<FolderEntry> {
        @Override
        protected void updateItem(FolderEntry item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.label());
        }
    }
}