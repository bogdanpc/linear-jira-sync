package bogdanpc.linearsync.linear.control;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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
}
