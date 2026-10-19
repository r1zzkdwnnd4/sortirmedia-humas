package id.ac.unjani.humas.sortirmedia.service;

import id.ac.unjani.humas.sortirmedia.model.MediaItem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileOperationService {

    private static final Logger log = LoggerFactory.getLogger(FileOperationService.class);

    public enum OperationMode {
        COPY, MOVE
    }

    public static Task<Void> createOperationTask(
            OperationMode mode,
            List<MediaGrouping.MediaGroup> groupsToProcess,
            Map<Integer, Path> flagDestinations,
            Path fallbackDestination) {

        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                int totalFiles = 0;
                // Hitung total file yang akan diproses
                for (MediaGrouping.MediaGroup group : groupsToProcess) {
                    totalFiles += group.members().size();
                }

                int processedFiles = 0;
                for (MediaGrouping.MediaGroup group : groupsToProcess) {
                    if (isCancelled()) {
                        break;
                    }

                    MediaItem primary = group.primary();
                    List<Path> targetDirs = new ArrayList<>();

                    // Tentukan direktori tujuan berdasarkan flag
                    for (int i = 0; i < 5; i++) {
                        if (primary.hasFlag(i) && flagDestinations.containsKey(i)) {
                            targetDirs.add(flagDestinations.get(i));
                        }
                    }

                    // Jika tidak ada flag yang cocok atau tidak diflag, gunakan fallback (jika ada)
                    if (targetDirs.isEmpty() && fallbackDestination != null) {
                        targetDirs.add(fallbackDestination);
                    }

                    if (targetDirs.isEmpty()) {
                        // Skip file ini karena tidak ada tujuan yang ditentukan
                        processedFiles += group.members().size();
                        updateProgress(processedFiles, totalFiles);
                        continue;
                    }

                    // Proses setiap member dalam grup (misalnya JPEG dan RAW)
                    for (MediaItem member : group.members()) {
                        Path sourcePath = member.getPath();
                        String fileName = member.getFileName();
                        boolean allSuccess = true;

                        // Copy ke semua tujuan
                        for (int i = 0; i < targetDirs.size(); i++) {
                            Path targetDir = targetDirs.get(i);
                            if (!Files.exists(targetDir)) {
                                Files.createDirectories(targetDir);
                            }

                            Path targetPath = resolveCollision(targetDir, fileName);

                            try {
                                if (mode == OperationMode.COPY || targetDirs.size() > 1) {
                                    // Jika mode COPY, atau mode MOVE tapi tujuannya lebih dari 1 (multi-flag)
                                    // Kita harus copy dulu.
                                    Files.copy(sourcePath, targetPath, StandardCopyOption.COPY_ATTRIBUTES);
                                    log.info("Copied {} to {}", sourcePath, targetPath);
                                } else {
                                    // Mode MOVE dengan 1 tujuan
                                    Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE);
                                    log.info("Moved {} to {}", sourcePath, targetPath);
                                }
                            } catch (IOException e) {
                                log.error("Failed to process {} to {}", sourcePath, targetPath, e);
                                allSuccess = false;
                                break; // Jangan lanjut ke target berikutnya jika gagal
                            }
                        }

                        // Jika mode MOVE dan multi-flag, kita hapus source setelah semua copy berhasil
                        if (mode == OperationMode.MOVE && targetDirs.size() > 1 && allSuccess) {
                            try {
                                Files.delete(sourcePath);
                                log.info("Deleted original file after multi-copy move: {}", sourcePath);
                            } catch (IOException e) {
                                log.error("Failed to delete original file: {}", sourcePath, e);
                            }
                        }

                        processedFiles++;
                        updateProgress(processedFiles, totalFiles);
                    }
                }
                return null;
            }
        };
    }

    private static Path resolveCollision(Path targetDir, String fileName) {
        Path targetPath = targetDir.resolve(fileName);
        if (!Files.exists(targetPath)) {
            return targetPath;
        }

        String baseName = fileName;
        String extension = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            baseName = fileName.substring(0, dot);
            extension = fileName.substring(dot);
        }

        int counter = 1;
        while (Files.exists(targetPath)) {
            targetPath = targetDir.resolve(baseName + "_" + counter + extension);
            counter++;
        }
        return targetPath;
    }
}
