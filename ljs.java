///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25+
//DEPS io.quarkus.platform:quarkus-bom:3.39.3@pom
//DEPS io.quarkus:quarkus-aesh
//DEPS io.quarkus:quarkus-rest-client-jackson
//DEPS io.quarkus:quarkus-arc
//JAVA_OPTIONS -XX:+UseCompactObjectHeaders
//JAVA_OPTIONS -Djava.util.logging.manager=org.jboss.logmanager.LogManager
//JAVA_OPTIONS --add-opens=java.base/java.lang=ALL-UNNAMED
//SOURCES src/main/java/**/*.java
//FILES src/main/resources/application.properties

import bogdanpc.linearsync.cli.boundary.Application;

public class sync {

  public static void main(String... args) {
    Application.main(args);
  }
}
