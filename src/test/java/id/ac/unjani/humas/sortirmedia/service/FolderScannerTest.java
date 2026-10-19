package id.ac.unjani.humas.sortirmedia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FolderScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void scanFolderMembacaSubfolderDanMediaItem() throws IOException {
        // Buat 2 subfolder
        Path sub1 = Files.createDirectory(tempDir.resolve("Dokumentasi"));
        Path sub2 = Files.createDirectory(tempDir.resolve("RawFiles"));

        // Buat file gambar dan file teks (yang harusnya diabaikan)
        Files.createFile(tempDir.resolve("foto1.jpg"));
        Files.createFile(tempDir.resolve("foto2.png"));
        Files.createFile(tempDir.resolve("catatan.txt"));

        FolderScanner.ScanResult result = FolderScanner.scanFolder(tempDir);

        assertEquals(2, result.subfolders().size());
        assertTrue(result.subfolders().contains(sub1));
        assertTrue(result.subfolders().contains(sub2));

        assertEquals(2, result.mediaItems().size());
        assertEquals("foto1.jpg", result.mediaItems().get(0).getFileName());
        assertEquals("foto2.png", result.mediaItems().get(1).getFileName());
    }
}
