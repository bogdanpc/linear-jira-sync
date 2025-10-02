///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25+
//DEPS io.quarkus.platform:quarkus-bom:3.28.1@pom
//DEPS io.quarkus:quarkus-picocli
//DEPS io.quarkus:quarkus-rest-client-jackson
//DEPS io.quarkus:quarkus-arc
//JAVAC_OPTIONS -parameters
//JAVA_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager
//JAVA_OPTIONS --add-opens=java.base/java.lang=ALL-UNNAMED
//SOURCES src/main/java/**/*.java
//FILES src/main/resources/application.properties
//Q:CONFIG quarkus.banner.enabled=false

import io.quarkus.runtime.Quarkus;

void main(String... args) {
    Quarkus.run(args);
}
