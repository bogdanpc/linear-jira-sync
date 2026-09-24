package bogdanpc.linearsync.linear.control;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "attachment")
public interface AttachmentConfig {

    @WithName("sync.enabled")
    @WithDefault("true")
    boolean syncEnabled();

    Download download();

    interface Download {

        @WithDefault("30")
        int timeout();

        @WithName("max-size")
        @WithDefault("10485760")
        int maxSize();
    }
}
