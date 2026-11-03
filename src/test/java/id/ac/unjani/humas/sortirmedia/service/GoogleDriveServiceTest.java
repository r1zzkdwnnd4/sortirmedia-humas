package id.ac.unjani.humas.sortirmedia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GoogleDriveServiceTest {

    @Test
    void formatMonthFolderNameSesuaiFormatHumas() {
        assertEquals("1. Januari", GoogleDriveService.getMonthFolderName(1));
        assertEquals("2. Februari", GoogleDriveService.getMonthFolderName(2));
        assertEquals("3. Maret", GoogleDriveService.getMonthFolderName(3));
        assertEquals("4. April", GoogleDriveService.getMonthFolderName(4));
        assertEquals("5. Mei", GoogleDriveService.getMonthFolderName(5));
        assertEquals("6. Juni", GoogleDriveService.getMonthFolderName(6));
        assertEquals("7. Juli", GoogleDriveService.getMonthFolderName(7));
        assertEquals("8. Agustus", GoogleDriveService.getMonthFolderName(8));
        assertEquals("9. September", GoogleDriveService.getMonthFolderName(9));
        assertEquals("10. Oktober", GoogleDriveService.getMonthFolderName(10));
        assertEquals("11. November", GoogleDriveService.getMonthFolderName(11));
        assertEquals("12. Desember", GoogleDriveService.getMonthFolderName(12));
    }

    @Test
    void formatActivityFolderNameSesuaiFormatYYMMDD() {
        LocalDate date = LocalDate.of(2026, 10, 13);
        String folderName = GoogleDriveService.formatActivityFolderName(date, "Dies Natalis UNJANI");
        assertEquals("261013 - Dies Natalis UNJANI", folderName);

        LocalDate dateAugust = LocalDate.of(2026, 8, 5);
        String augustFolder = GoogleDriveService.formatActivityFolderName(dateAugust, "Peliputan Humas");
        assertEquals("260805 - Peliputan Humas", augustFolder);
    }

    @Test
    void parseFolderActivityMengekstrakTanggalDanNamaKegiatan() {
        // Format YYMMDD
        GoogleDriveService.FolderActivityInfo info1 = GoogleDriveService.parseFolderActivity("241025 - Dies Natalis UNJANI");
        assertEquals(LocalDate.of(2024, 10, 25), info1.date());
        assertEquals("Dies Natalis UNJANI", info1.activityName());

        // Format YYYYMMDD
        GoogleDriveService.FolderActivityInfo info2 = GoogleDriveService.parseFolderActivity("20241025 - Rapat Senat");
        assertEquals(LocalDate.of(2024, 10, 25), info2.date());
        assertEquals("Rapat Senat", info2.activityName());

        // Format YYYY-MM-DD
        GoogleDriveService.FolderActivityInfo info3 = GoogleDriveService.parseFolderActivity("2026-11-03 - Kunjungan Kerja");
        assertEquals(LocalDate.of(2026, 11, 3), info3.date());
        assertEquals("Kunjungan Kerja", info3.activityName());

        // Format DD-MM-YYYY
        GoogleDriveService.FolderActivityInfo info4 = GoogleDriveService.parseFolderActivity("05-08-2026 - Peliputan");
        assertEquals(LocalDate.of(2026, 8, 5), info4.date());
        assertEquals("Peliputan", info4.activityName());

        // Tanpa format tanggal
        GoogleDriveService.FolderActivityInfo info5 = GoogleDriveService.parseFolderActivity("Dokumentasi Rapat Pimpinan");
        assertNull(info5.date());
        assertEquals("Dokumentasi Rapat Pimpinan", info5.activityName());

        // Kosong / null
        GoogleDriveService.FolderActivityInfo info6 = GoogleDriveService.parseFolderActivity("");
        assertNull(info6.date());
        assertEquals("", info6.activityName());
    }

    @Test
    void instanceSingletonTersedia() {
        assertNotNull(GoogleDriveService.getInstance());
    }
}
