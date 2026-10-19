package id.ac.unjani.humas.sortirmedia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void instanceSingletonTersedia() {
        assertNotNull(GoogleDriveService.getInstance());
    }
}
