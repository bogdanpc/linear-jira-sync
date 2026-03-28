package bogdanpc.linearsync.linear.control;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

@ConfigMapping(prefix = "linear")
public interface LinearConfig {

    Api api();

    interface Api {

        Optional<String> token();

        @WithDefault("https://api.linear.app/graphql")
        String url();
    }
}
