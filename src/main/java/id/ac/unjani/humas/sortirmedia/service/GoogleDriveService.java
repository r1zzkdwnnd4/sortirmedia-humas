package id.ac.unjani.humas.sortirmedia.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.FileList;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service untuk integrasi Google Drive v3 dan OAuth2 Desktop (Fase 6).
 * Menyimpan token di ~/.sortir-media/tokens agar tidak perlu login berulang.
 */
public class GoogleDriveService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
    private static final String APPLICATION_NAME = "Sortir Media Humas";

    private static final Path APP_DIR = Path.of(System.getProperty("user.home"), ".sortir-media");
    private static final Path TOKENS_DIR = APP_DIR.resolve("tokens");
    private static final Path CONFIG_FILE = APP_DIR.resolve("config.properties");
    private static final Path DEFAULT_USER_CREDENTIALS = APP_DIR.resolve("credentials.json");

    private static final String PROP_HUMAS_FOLDER_ID = "humas.folder.id";
    private static final String PROP_CREDENTIALS_PATH = "credentials.path";

    private static GoogleDriveService instance;

    public static synchronized GoogleDriveService getInstance() {
        if (instance == null) {
            instance = new GoogleDriveService();
        }
        return instance;
    }

    private GoogleDriveService() {
        try {
            Files.createDirectories(APP_DIR);
            Files.createDirectories(TOKENS_DIR);
        } catch (IOException e) {
            log.error("Gagal membuat direktori aplikasi di {}", APP_DIR, e);
        }
    }

    /**
     * Nama folder bulan berformat: "8. Agustus", dst.
     */
    public static String getMonthFolderName(int month) {
        String[] months = {
            "1. Januari", "2. Februari", "3. Maret", "4. April",
            "5. Mei", "6. Juni", "7. Juli", "8. Agustus",
            "9. September", "10. Oktober", "11. November", "12. Desember"
        };
        if (month >= 1 && month <= 12) {
            return months[month - 1];
        }
        return month + ". Bulan " + month;
    }

    /**
     * Membentuk nama folder kegiatan: "YYMMDD - Nama Kegiatan"
     */
    public static String formatActivityFolderName(LocalDate date, String activityName) {
        Objects.requireNonNull(date, "date");
        String prefix = date.format(DateTimeFormatter.ofPattern("yyMMdd"));
        String cleanName = activityName == null ? "" : activityName.trim();
        if (cleanName.isEmpty()) {
            return prefix;
        }
        return prefix + " - " + cleanName;
    }

    /**
     * Memeriksa keberadaan file credentials.json / client_secret.json.
     */
    public Path findCredentialsPath() {
        // 1. Dari config.properties jika diset pengguna
        String customPath = getConfigProperty(PROP_CREDENTIALS_PATH);
        if (customPath != null && !customPath.isBlank()) {
            Path p = Path.of(customPath);
            if (Files.exists(p)) {
                return p;
            }
        }

        // 2. Di working directory
        Path localCreds = Path.of("credentials.json");
        if (Files.exists(localCreds)) {
            return localCreds;
        }
        Path localSecret = Path.of("client_secret.json");
        if (Files.exists(localSecret)) {
            return localSecret;
        }

        // 3. Di ~/.sortir-media/credentials.json
        if (Files.exists(DEFAULT_USER_CREDENTIALS)) {
            return DEFAULT_USER_CREDENTIALS;
        }

        return null;
    }

    public boolean isCredentialsAvailable() {
        return findCredentialsPath() != null || getClass().getResourceAsStream("/credentials.json") != null;
    }

    public void saveCredentialsFile(Path sourceFile) throws IOException {
        Files.createDirectories(APP_DIR);
        Files.copy(sourceFile, DEFAULT_USER_CREDENTIALS, StandardCopyOption.REPLACE_EXISTING);
        setConfigProperty(PROP_CREDENTIALS_PATH, DEFAULT_USER_CREDENTIALS.toAbsolutePath().toString());
        log.info("Credentials disimpan ke {}", DEFAULT_USER_CREDENTIALS);
    }

    public String getConfiguredHumasFolderId() {
        return getConfigProperty(PROP_HUMAS_FOLDER_ID);
    }

    public void setConfiguredHumasFolderId(String folderId) {
        setConfigProperty(PROP_HUMAS_FOLDER_ID, folderId == null ? "" : folderId.trim());
    }

    private String getConfigProperty(String key) {
        if (!Files.exists(CONFIG_FILE)) {
            return "";
        }
        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            props.load(r);
            return props.getProperty(key, "");
        } catch (IOException e) {
            log.warn("Gagal membaca config.properties: {}", e.getMessage());
            return "";
        }
    }

    private void setConfigProperty(String key, String value) {
        Properties props = new Properties();
        if (Files.exists(CONFIG_FILE)) {
            try (Reader r = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
                props.load(r);
            } catch (IOException e) {
                log.warn("Gagal membaca config.properties sebelum update: {}", e.getMessage());
            }
        }
        props.setProperty(key, value);
        try (Writer w = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
            props.store(w, "SortirMedia Configuration");
        } catch (IOException e) {
            log.error("Gagal menyimpan config.properties: {}", e.getMessage());
        }
    }

    private InputStream openCredentialsStream() throws IOException {
        Path path = findCredentialsPath();
        if (path != null && Files.exists(path)) {
            log.info("Menggunakan credentials dari file: {}", path.toAbsolutePath());
            return new FileInputStream(path.toFile());
        }
        InputStream resStream = getClass().getResourceAsStream("/credentials.json");
        if (resStream != null) {
            log.info("Menggunakan credentials dari classpath: /credentials.json");
            return resStream;
        }
        throw new IOException("File credentials.json tidak ditemukan. Silakan pasang file credentials OAuth2.");
    }

    private GoogleAuthorizationCodeFlow createFlow(NetHttpTransport httpTransport) throws IOException {
        try (InputStream in = openCredentialsStream();
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
            return new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(TOKENS_DIR.toFile()))
                    .setAccessType("offline")
                    .build();
        }
    }

    /**
     * Memeriksa apakah token sudah tersimpan di direktori tokens.
     */
    public boolean isAuthorized() {
        try {
            if (!isCredentialsAvailable()) {
                return false;
            }
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            GoogleAuthorizationCodeFlow flow = createFlow(httpTransport);
            Credential credential = flow.loadCredential("user");
            return credential != null && (credential.getAccessToken() != null || credential.getRefreshToken() != null);
        } catch (Exception e) {
            log.warn("Pemeriksaan status login gagal: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Melakukan login OAuth2 jika belum ada token, atau mengambil credential yang ada.
     */
    public Credential authorize() throws Exception {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleAuthorizationCodeFlow flow = createFlow(httpTransport);
        Credential credential = flow.loadCredential("user");

        if (credential != null && credential.getRefreshToken() != null) {
            log.info("Menggunakan token tersimpan di {}", TOKENS_DIR);
            return credential;
        }

        log.info("Memulai OAuth2 authorization flow melalui browser...");
        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888)
                .setCallbackPath("/Callback")
                .build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    /**
     * Menghapus token tersimpan untuk logout / ganti akun.
     */
    public void logout() {
        try {
            if (Files.exists(TOKENS_DIR)) {
                try (var stream = Files.list(TOKENS_DIR)) {
                    for (Path file : stream.toList()) {
                        Files.deleteIfExists(file);
                    }
                }
            }
            log.info("Berhasil logout: token dihapus dari {}", TOKENS_DIR);
        } catch (IOException e) {
            log.error("Gagal menghapus token di {}", TOKENS_DIR, e);
        }
    }

    /**
     * Membuat instance Drive client.
     */
    public Drive getDriveService() throws Exception {
        Credential credential = authorize();
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        return new Drive.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Mendapatkan email pengguna yang saat ini login.
     */
    public String getUserAccountEmail() {
        try {
            Drive drive = getDriveService();
            About about = drive.about().get().setFields("user(displayName,emailAddress)").execute();
            if (about.getUser() != null) {
                return about.getUser().getEmailAddress() + " (" + about.getUser().getDisplayName() + ")";
            }
        } catch (Exception e) {
            log.warn("Gagal membaca profil pengguna Google Drive: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Mencari atau membuat folder induk Humas di Google Drive.
     * Jika folderId diberikan di config, memvalidasi ID tersebut.
     * Jika tidak, mencari folder "Humas" di root atau membuatnya jika belum ada.
     */
    public String getOrCreateHumasFolder(Drive drive, String configuredIdOrName) throws IOException {
        String target = configuredIdOrName == null ? "" : configuredIdOrName.trim();

        if (!target.isEmpty() && !target.equalsIgnoreCase("Humas")) {
            try {
                com.google.api.services.drive.model.File file = drive.files().get(target)
                        .setFields("id, name, mimeType, trashed")
                        .execute();
                if (file != null && !Boolean.TRUE.equals(file.getTrashed())
                        && "application/vnd.google-apps.folder".equals(file.getMimeType())) {
                    log.info("Menggunakan folder Humas dari ID terkonfigurasi: {} ({})", file.getName(), file.getId());
                    return file.getId();
                }
            } catch (Exception e) {
                log.warn("Folder ID '{}' tidak valid atau tidak bisa diakses, mencari folder 'Humas'...", target);
            }
        }

        // Cari folder bernama "Humas" yang aktif
        String query = "mimeType = 'application/vnd.google-apps.folder' and name = 'Humas' and trashed = false";
        FileList result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .setPageSize(5)
                .execute();

        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            String folderId = result.getFiles().get(0).getId();
            log.info("Ditemukan folder Humas yang sudah ada: {}", folderId);
            return folderId;
        }

        // Buat folder "Humas" baru di root
        com.google.api.services.drive.model.File folderMetadata = new com.google.api.services.drive.model.File();
        folderMetadata.setName("Humas");
        folderMetadata.setMimeType("application/vnd.google-apps.folder");

        com.google.api.services.drive.model.File created = drive.files().create(folderMetadata)
                .setFields("id, name")
                .execute();
        log.info("Berhasil membuat folder Humas baru di root: {}", created.getId());
        return created.getId();
    }

    /**
     * Mencari atau membuat folder tahun (misal "2026") di bawah folder induk Humas.
     */
    public String getOrCreateYearFolder(Drive drive, String parentFolderId, int year) throws IOException {
        String yearFolderName = String.valueOf(year);

        String query = "mimeType = 'application/vnd.google-apps.folder' and name = '" 
                + escapeQuery(yearFolderName) + "' and '" + parentFolderId + "' in parents and trashed = false";
        FileList result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .setPageSize(5)
                .execute();

        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            String folderId = result.getFiles().get(0).getId();
            log.info("Ditemukan folder tahun '{}' (ID: {})", yearFolderName, folderId);
            return folderId;
        }

        // Buat folder tahun baru
        com.google.api.services.drive.model.File folderMetadata = new com.google.api.services.drive.model.File();
        folderMetadata.setName(yearFolderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        folderMetadata.setParents(Collections.singletonList(parentFolderId));

        com.google.api.services.drive.model.File created = drive.files().create(folderMetadata)
                .setFields("id, name")
                .execute();
        log.info("Berhasil membuat folder tahun baru '{}' (ID: {}) di bawah {}", yearFolderName, created.getId(), parentFolderId);
        return created.getId();
    }

    /**
     * Mencari atau membuat folder bulan (misal "8. Agustus", "10. Oktober") di bawah folder tahun.
     */
    public String getOrCreateMonthFolder(Drive drive, String parentFolderId, int monthNumber) throws IOException {
        String monthFolderName = getMonthFolderName(monthNumber);

        String query = "mimeType = 'application/vnd.google-apps.folder' and name = '" 
                + escapeQuery(monthFolderName) + "' and '" + parentFolderId + "' in parents and trashed = false";
        FileList result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .setPageSize(5)
                .execute();

        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            String folderId = result.getFiles().get(0).getId();
            log.info("Ditemukan folder bulan '{}' (ID: {})", monthFolderName, folderId);
            return folderId;
        }

        // Buat folder bulan baru
        com.google.api.services.drive.model.File folderMetadata = new com.google.api.services.drive.model.File();
        folderMetadata.setName(monthFolderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        folderMetadata.setParents(Collections.singletonList(parentFolderId));

        com.google.api.services.drive.model.File created = drive.files().create(folderMetadata)
                .setFields("id, name")
                .execute();
        log.info("Berhasil membuat folder bulan baru '{}' (ID: {}) di bawah parent {}", monthFolderName, created.getId(), parentFolderId);
        return created.getId();
    }

    /**
     * Mencari atau membuat folder kegiatan (misal "261013 - Wisuda") di bawah folder bulan.
     */
    public String getOrCreateActivityFolder(Drive drive, String monthFolderId, LocalDate date, String activityName) throws IOException {
        String activityFolderName = formatActivityFolderName(date, activityName);

        String query = "mimeType = 'application/vnd.google-apps.folder' and name = '" 
                + escapeQuery(activityFolderName) + "' and '" + monthFolderId + "' in parents and trashed = false";
        FileList result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .setPageSize(5)
                .execute();

        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            String folderId = result.getFiles().get(0).getId();
            log.info("Ditemukan folder kegiatan '{}' (ID: {})", activityFolderName, folderId);
            return folderId;
        }

        com.google.api.services.drive.model.File folderMetadata = new com.google.api.services.drive.model.File();
        folderMetadata.setName(activityFolderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        folderMetadata.setParents(Collections.singletonList(monthFolderId));

        com.google.api.services.drive.model.File created = drive.files().create(folderMetadata)
                .setFields("id, name")
                .execute();
        log.info("Berhasil membuat folder kegiatan '{}' (ID: {}) di bawah folder bulan", activityFolderName, created.getId());
        return created.getId();
    }

    /**
     * Mengunggah satu file ke folder kegiatan di Google Drive dengan Resumable Upload dan Retry.
     */
    public com.google.api.services.drive.model.File uploadFileWithRetry(
            Drive drive,
            String parentFolderId,
            Path localFilePath,
            UploadProgressListener listener,
            int maxRetries) throws IOException, InterruptedException {

        File fileToUpload = localFilePath.toFile();
        long fileSize = fileToUpload.length();
        String fileName = localFilePath.getFileName().toString();
        String mimeType = determineMimeType(localFilePath);

        int attempts = 0;
        long backoffDelayMs = 1500;

        while (true) {
            attempts++;
            try {
                com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
                fileMetadata.setName(fileName);
                fileMetadata.setParents(Collections.singletonList(parentFolderId));

                FileContent mediaContent = new FileContent(mimeType, fileToUpload);
                Drive.Files.Create createRequest = drive.files().create(fileMetadata, mediaContent);
                createRequest.setFields("id, name, size");

                MediaHttpUploader uploader = createRequest.getMediaHttpUploader();
                // Resumable upload aktif
                uploader.setDirectUploadEnabled(false);
                // Ukuran chunk 1 MB untuk pembaruan progress yang responsif
                uploader.setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE * 4);

                if (listener != null) {
                    uploader.setProgressListener(u -> {
                        try {
                            switch (u.getUploadState()) {
                                case INITIATION_STARTED -> listener.onStatusUpdate("Memulai upload resumable: " + fileName);
                                case MEDIA_IN_PROGRESS -> {
                                    double progress = u.getProgress();
                                    long bytesUploaded = (long) (progress * fileSize);
                                    listener.onProgress(fileName, bytesUploaded, fileSize, progress);
                                }
                                case MEDIA_COMPLETE -> listener.onProgress(fileName, fileSize, fileSize, 1.0);
                                default -> {}
                            }
                        } catch (Exception e) {
                            log.debug("Listener update error: {}", e.getMessage());
                        }
                    });
                }

                log.info("Mengunggah {} ({} bytes, attempt {}) ke Drive...", fileName, fileSize, attempts);
                com.google.api.services.drive.model.File uploadedFile = createRequest.execute();
                log.info("Upload berhasil: {} (ID: {})", fileName, uploadedFile.getId());
                return uploadedFile;

            } catch (IOException e) {
                log.warn("Gagal mengunggah {} pada percobaan ke-{}: {}", fileName, attempts, e.getMessage());
                if (attempts >= maxRetries) {
                    throw e;
                }
                if (listener != null) {
                    listener.onStatusUpdate("Percobaan ke-" + attempts + " gagal untuk " + fileName + ". Mengulang dalam " + (backoffDelayMs / 1000) + " detik...");
                }
                Thread.sleep(backoffDelayMs);
                backoffDelayMs = Math.min(backoffDelayMs * 2, 15000);
            }
        }
    }

    private static String determineMimeType(Path path) {
        try {
            String probe = Files.probeContentType(path);
            if (probe != null && !probe.isBlank()) {
                return probe;
            }
        } catch (IOException ignored) {}

        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".mov")) return "video/quicktime";
        if (name.endsWith(".mkv")) return "video/x-matroska";
        if (name.endsWith(".avi")) return "video/x-msvideo";
        if (name.endsWith(".arw") || name.endsWith(".cr2") || name.endsWith(".nef") || name.endsWith(".dng")) {
            return "application/octet-stream";
        }
        return "application/octet-stream";
    }

    private static String escapeQuery(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    @FunctionalInterface
    public interface UploadProgressListener {
        void onProgress(String fileName, long bytesUploaded, long totalBytes, double progressFraction);
        default void onStatusUpdate(String status) {}
    }
}
