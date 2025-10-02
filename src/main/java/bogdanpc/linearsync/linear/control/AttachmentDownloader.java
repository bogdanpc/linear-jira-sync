package bogdanpc.linearsync.linear.control;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Optional;

@ApplicationScoped
public class AttachmentDownloader {

    @ConfigProperty(name = "linear.api.token")
    Optional<String> linearApiToken;

    @ConfigProperty(name = "attachment.download.timeout", defaultValue = "30")
    int timeoutSeconds;

    @ConfigProperty(name = "attachment.download.max-size", defaultValue = "10485760") // 10MB
    long maxFileSizeBytes;

    private final HttpClient httpClient;

    public AttachmentDownloader() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public Optional<File> downloadAttachment(String attachmentId, String attachmentUrl, String filename) {
        if (attachmentId == null || attachmentId.trim().isEmpty()) {
            Log.warnf("Invalid attachment ID provided: %s", attachmentId);
            return Optional.empty();
        }

        if (attachmentUrl == null || attachmentUrl.trim().isEmpty()) {
            Log.warnf("No URL provided for attachment %s", attachmentId);
            return Optional.empty();
        }

        if (!isValidUrl(attachmentUrl)) {
            Log.warnf("Invalid URL format for attachment %s: %s", attachmentId, attachmentUrl);
            return Optional.empty();
        }

        try {
            Log.debugf("Downloading attachment %s from %s", attachmentId, attachmentUrl);

            var request = buildRequest(attachmentUrl);
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                Log.warnf("Failed to download attachment %s. HTTP status: %d", attachmentId, response.statusCode());
                return Optional.empty();
            }

            var contentLength = response.headers().firstValueAsLong("content-length");
            if (contentLength.isPresent() && contentLength.getAsLong() > maxFileSizeBytes) {
                Log.warnf("Attachment %s is too large (%d bytes). Max allowed: %d bytes",
                         attachmentId, contentLength.getAsLong(), maxFileSizeBytes);
                return Optional.empty();
            }

            var tempFile = createTempFile(filename);
            if (writeToFile(response.body(), tempFile, attachmentId)) {
                Log.debugf("Successfully downloaded attachment %s to %s", attachmentId, tempFile.getAbsolutePath());
                return Optional.of(tempFile);
            } else {
                return Optional.empty();
            }

        } catch (Exception e) {
            Log.errorf(e, "Failed to download attachment %s from %s", attachmentId, attachmentUrl);
            return Optional.empty();
        }
    }

    private HttpRequest buildRequest(String url) {
        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .GET();

        if (linearApiToken.isPresent() && !linearApiToken.get().isEmpty()) {
            requestBuilder.header("Authorization", linearApiToken.get());
        }

        return requestBuilder.build();
    }

    private File createTempFile(String originalFilename) throws IOException {
        var sanitizedFilename = sanitizeFilename(originalFilename);
        var extension = getFileExtension(sanitizedFilename);
        var baseName = getBaseName(sanitizedFilename);

        return Files.createTempFile("linear-attachment-" + baseName + "-", extension).toFile();
    }

    private boolean writeToFile(InputStream inputStream, File tempFile, String attachmentId) {
        try (var fos = new FileOutputStream(tempFile);
             var is = inputStream) {

            var buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = is.read(buffer)) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > maxFileSizeBytes) {
                    Log.warnf("Attachment %s exceeded max size during download. Aborting.", attachmentId);
                    tempFile.delete();
                    return false;
                }
                fos.write(buffer, 0, bytesRead);
            }

            Log.debugf("Downloaded %d bytes for attachment %s", totalBytes, attachmentId);
            return true;

        } catch (Exception e) {
            Log.errorf(e, "Failed to write attachment %s to file %s", attachmentId, tempFile.getAbsolutePath());
            if (tempFile.exists()) {
                tempFile.delete();
            }
            return false;
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "attachment";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String getFileExtension(String filename) {
        var lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot);
        }
        return ".tmp";
    }

    private String getBaseName(String filename) {
        var lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }

    public void cleanupTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            try {
                if (tempFile.delete()) {
                    Log.debugf("Cleaned up temporary file: %s", tempFile.getAbsolutePath());
                } else {
                    Log.warnf("Failed to delete temporary file: %s", tempFile.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.warnf(e, "Error cleaning up temporary file: %s", tempFile.getAbsolutePath());
            }
        }
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }

        try {
            var uri = URI.create(url);
            return uri.getHost() != null;
        } catch (IllegalArgumentException _) {
            return false;
        }
    }
}