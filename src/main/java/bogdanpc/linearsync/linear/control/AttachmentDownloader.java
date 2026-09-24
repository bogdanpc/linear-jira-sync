package bogdanpc.linearsync.linear.control;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Optional;

@ApplicationScoped
public class AttachmentDownloader {

    private static final String LINEAR_UPLOAD_HOST = "uploads.linear.app";

    private final LinearConfig linearConfig;
    private final AttachmentConfig attachmentConfig;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    AttachmentDownloader(LinearConfig linearConfig, AttachmentConfig attachmentConfig) {
        this.linearConfig = linearConfig;
        this.attachmentConfig = attachmentConfig;
    }

    public static boolean isLinearUpload(String url) {
        if (url == null) {
            return false;
        }
        try {
            var uri = URI.create(url);
            return "https".equals(uri.getScheme()) && LINEAR_UPLOAD_HOST.equalsIgnoreCase(uri.getHost());
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    public Optional<File> downloadAttachment(String attachmentId, String url, String filename) {
        if (!isLinearUpload(url)) {
            Log.warnf("Refusing to download attachment %s from non-Linear URL: %s", attachmentId, url);
            return Optional.empty();
        }

        try {
            var content = fetch(attachmentId, url);
            if (content.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(writeTempFile(filename, content.get()));
        } catch (IOException e) {
            Log.errorf(e, "Failed to download attachment %s from %s", attachmentId, url);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.errorf(e, "Download interrupted for attachment %s", attachmentId);
            return Optional.empty();
        }
    }

    public void cleanupTempFile(File file) {
        try {
            Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(file.toPath().getParent());
        } catch (IOException e) {
            Log.warnf(e, "Failed to delete temporary file %s", file);
        }
    }

    private Optional<byte[]> fetch(String attachmentId, String url) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(attachmentConfig.download().timeout()))
                .header("Authorization", "Bearer " + linearConfig.api().token().orElseThrow())
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) {
                Log.warnf("Failed to download attachment %s. HTTP status: %d", attachmentId, response.statusCode());
                return Optional.empty();
            }

            var maxSize = attachmentConfig.download().maxSize();
            var content = body.readNBytes(maxSize + 1);
            if (content.length > maxSize) {
                Log.warnf("Attachment %s exceeds the maximum size of %d bytes", attachmentId, maxSize);
                return Optional.empty();
            }
            return Optional.of(content);
        }
    }

    private static File writeTempFile(String filename, byte[] content) throws IOException {
        var name = filename == null || filename.isBlank()
                ? "attachment"
                : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        var file = Files.createTempDirectory("linear-attachment-").resolve(name);
        return Files.write(file, content).toFile();
    }
}
