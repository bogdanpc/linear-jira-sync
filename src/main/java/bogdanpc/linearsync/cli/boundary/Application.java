package bogdanpc.linearsync.cli.boundary;

import io.quarkus.aesh.runtime.AeshRuntimeRunnerFactory;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;

@QuarkusMain
public class Application implements QuarkusApplication {

    @Inject
    AeshRuntimeRunnerFactory runnerFactory;

    @Override
    public int run(String... args) {
        var result = runnerFactory.create().args(args).execute();
        return result.isSuccess() ? 0 : result.getExitCode();
    }

    static void main(String... args) {
        BootstrapConfig.apply(args);
        Quarkus.run(Application.class, args);
    }
}
