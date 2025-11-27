///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25+
//DEPS io.quarkus.platform:quarkus-bom:3.30.1@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS io.quarkus:quarkus-rest-client-jackson
//DEPS io.quarkus:quarkus-arc
//JAVA_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager
//JAVA_OPTIONS --add-opens=java.base/java.lang=ALL-UNNAMED
//SOURCES src/main/java/**/*.java
//FILES src/main/resources/application.properties


import bogdanpc.linearsync.cli.boundary.LinearJiraSyncCommand;

import picocli.CommandLine;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import jakarta.inject.Inject;

@QuarkusMain
public class sync implements QuarkusApplication {

    @Inject
    CommandLine.IFactory factory;

    @Inject
    @TopCommand
    LinearJiraSyncCommand command;

    @Override
    public int run(String... args) throws Exception {
        return new CommandLine(command, factory).execute(args);
    }

    public static void main(String... args) {
        Quarkus.run(sync.class, args);
    }
}
