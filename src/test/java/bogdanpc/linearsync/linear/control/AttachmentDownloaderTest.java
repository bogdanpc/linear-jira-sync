package bogdanpc.linearsync.linear.control;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentDownloaderTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://uploads.linear.app/abc/file.png",
            "https://UPLOADS.linear.app/abc/file.png"
    })
    void acceptsLinearUploads(String url) {
        assertTrue(AttachmentDownloader.isLinearUpload(url));
    }

    // The Linear API token is sent to these URLs, so anything but the upload host must be rejected
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "http://uploads.linear.app/abc/file.png",
            "https://evil.example/?uploads.linear.app",
            "https://uploads.linear.app.evil.example/file.png",
            "https://evil.example/uploads.linear.app/file.png",
            "https://uploads.linear.app@evil.example/file.png",
            "not a url"
    })
    void rejectsEverythingElse(String url) {
        assertFalse(AttachmentDownloader.isLinearUpload(url));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "NULL, attachment",
            "'', attachment",
            "., attachment",
            ".., attachment",
            "../../etc/passwd, .._.._etc_passwd",
            "report v2.pdf, report_v2.pdf"
    })
    void sanitizesFilenames(String title, String expected) {
        assertEquals(expected, AttachmentDownloader.safeFilename(title));
    }

    @Test
    void cleanupLeavesFilesOutsideItsTempDirectoriesAlone(@TempDir Path directory) throws IOException {
        var file = Files.writeString(directory.resolve("keep.txt"), "content");

        new AttachmentDownloader(null, null).cleanupTempFile(file.toFile());

        assertTrue(Files.exists(file));
    }

    @Test
    void cleanupDeletesDownloadedFileAndItsDirectory() throws IOException {
        var directory = Files.createTempDirectory("linear-attachment-");
        var file = Files.writeString(directory.resolve("report.pdf"), "content");

        new AttachmentDownloader(null, null).cleanupTempFile(file.toFile());

        assertFalse(Files.exists(directory));
    }
}
