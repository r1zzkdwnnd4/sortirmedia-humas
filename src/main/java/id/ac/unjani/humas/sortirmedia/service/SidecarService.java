package id.ac.unjani.humas.sortirmedia.service;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.io.Reader;
import java.io.Writer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SidecarService {
    private static final Logger log = LoggerFactory.getLogger(SidecarService.class);
    private static final String SIDECAR_FILE = ".sm-flags.properties";

    public static void loadFlags(Path folder, List<MediaItem> items) {
        Path sidecarPath = folder.resolve(SIDECAR_FILE);
        if (!Files.exists(sidecarPath)) {
            return;
        }

        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(sidecarPath)) {
            props.load(reader);
            for (MediaItem item : items) {
                String val = props.getProperty(item.getFileName());
                if (val != null) {
                    try {
                        item.setFlags(Integer.parseInt(val));
                    } catch (NumberFormatException e) {
                        log.warn("Format flag invalid untuk {}: {}", item.getFileName(), val);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Gagal membaca sidecar file: {}", sidecarPath, e);
        }
    }

    public static void saveFlags(Path folder, List<MediaItem> items) {
        Path sidecarPath = folder.resolve(SIDECAR_FILE);
        Properties props = new Properties();

        for (MediaItem item : items) {
            if (item.getFlags() != 0) {
                props.setProperty(item.getFileName(), String.valueOf(item.getFlags()));
            }
        }

        if (props.isEmpty()) {
            try {
                Files.deleteIfExists(sidecarPath);
            } catch (IOException e) {
                log.error("Gagal menghapus sidecar file yang kosong: {}", sidecarPath, e);
            }
            return;
        }

        try (Writer writer = Files.newBufferedWriter(sidecarPath)) {
            props.store(writer, "SortirMedia Flags");
        } catch (IOException e) {
            log.error("Gagal menyimpan sidecar file: {}", sidecarPath, e);
        }
    }
}
