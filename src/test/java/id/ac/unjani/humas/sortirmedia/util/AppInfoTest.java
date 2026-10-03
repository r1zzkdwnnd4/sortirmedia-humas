package id.ac.unjani.humas.sortirmedia.util;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class AppInfoTest {

    @Test
    void identitasAplikasiTidakKosong() {
        assertFalse(AppInfo.NAME.isBlank());
        assertFalse(AppInfo.VERSION.isBlank());
    }
}
