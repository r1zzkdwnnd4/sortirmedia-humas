package id.ac.unjani.humas.sortirmedia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import id.ac.unjani.humas.sortirmedia.model.MediaType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    @Test
    void scanFolderMembacaRawDanJpegBersandingan() throws IOException {
        Files.createFile(tempDir.resolve("DSC_0012.ARW"));
        Files.createFile(tempDir.resolve("DSC_0012.JPG"));
        Files.createFile(tempDir.resolve("IMG_0001.CR2"));
        Files.createFile(tempDir.resolve("IMG_0001.JPG"));

        List<MediaItem> items = FolderScanner.scan(tempDir);
        assertEquals(4, items.size());

        assertEquals("DSC_0012.ARW", items.get(0).getFileName());
        assertEquals(MediaType.RAW, items.get(0).getType());

        assertEquals("DSC_0012.JPG", items.get(1).getFileName());
        assertEquals(MediaType.PHOTO, items.get(1).getType());

        assertEquals("IMG_0001.CR2", items.get(2).getFileName());
        assertEquals(MediaType.RAW, items.get(2).getType());

        assertEquals("IMG_0001.JPG", items.get(3).getFileName());
        assertEquals(MediaType.PHOTO, items.get(3).getType());

        List<MediaGrouping.MediaGroup> groups = MediaGrouping.group(items);
        assertEquals(4, groups.size());
        assertEquals("DSC_0012.ARW", groups.get(0).primary().getFileName());
        assertEquals("DSC_0012.JPG", groups.get(1).primary().getFileName());
    }

    @Test
    void scanRecursiveMembacaFileMediaDiSubfolderTerdalam() throws IOException {
        Path folderA = Files.createDirectory(tempDir.resolve("Folder A"));
        Path folderB = Files.createDirectory(folderA.resolve("Folder B"));
        Path folderC = Files.createDirectory(folderB.resolve("Folder C"));

        Files.createFile(folderC.resolve("DSC_0001.ARW"));
        Files.createFile(folderC.resolve("DSC_0001.JPG"));
        Files.createFile(folderB.resolve("VIDEO_01.MP4"));

        List<MediaItem> items = FolderScanner.scanRecursive(folderA);
        assertEquals(3, items.size());
        assertEquals("VIDEO_01.MP4", items.get(0).getFileName());
        assertEquals("DSC_0001.ARW", items.get(1).getFileName());
        assertEquals("DSC_0001.JPG", items.get(2).getFileName());
    }
}
