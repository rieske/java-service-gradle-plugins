# Gradle plugins for building Java services

[![Actions Status](https://github.com/rieske/java-service-gradle-plugins/workflows/master/badge.svg)](https://github.com/rieske/java-service-gradle-plugins/actions/workflows/master.yml)
[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/io/github/rieske/java-service-gradle-plugins/plugins/maven-metadata.xml.svg?colorB=007ec6&label=Plugin%20Portal)](https://plugins.gradle.org/plugin/io.github.rieske.java-service)

## io.github.rieske.java-service

### Requirements

Requires at least:
- Java 11
- Gradle 7.3 (the plugin uses the [JVM Test Suite Plugin](https://docs.gradle.org/current/userguide/jvm_test_suite_plugin.html))

### Description

An opinionated plugin for building and testing a Java service:
- Compile and unit test the components as usual
- Package the application using Gradle's core `application` plugin.
Might play with Spring and such as well, but I'm not a fan of all that heavyweight magic -
just pick a library for a server, a library for JDBC or whatever, a library for something else, assemble everything in main() and be good to go.
- Package the service for deployment in a docker image
- [Black box test](https://github.com/rieske/black-box-testing) the produced docker image - unit testing the bits and pieces is not enough - the artifact has to be tested when it is fully assembled as well.
Far too often I have seen services with high unit test coverage percentage fail to even start up when assembled for deployment.

Configures a reproducible build that packages the Java application (using Gradle's core `application` plugin)
in a tar file and configures a `docker` task for further
packaging of the built artifact into a docker image.
The resulting docker image will be tagged with `${rootProject.name}:snapshot` and you can then
retag it after the build and push it to some docker registry for deployment.
The `rootProject.name` is configured in the `settings.gradle` file (and yes, this makes an
assumption of one service per project build (and hopefully per repository too!)).

The Dockerfile should be provided by the user - there are no assumptions made about how the base
image should be configured.
The easiest way to package the built application is to extract the built tar file and set the entrypoint like this:
```Dockerfile
# FROM directive and other base image setup goes here
ENV SERVICE_NAME=my-service
ENTRYPOINT /opt/service/$SERVICE_NAME/bin/$SERVICE_NAME
ADD $SERVICE_NAME.tar /opt/service/
```

Configures `blackBoxTest` source set that depends on the service's docker image.
This allows to test the produced artifact before it is published/deployed.
The `blackBoxTest` task is configured to be rerun only on production artifact (docker image), and
the `blackBoxTest` source set changes.

Usage:
```groovy
plugins {
    id("io.github.rieske.java-service").version("1.0.0")
}

java {
    // Configure the Java extension as usual
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    // Specify the class with the main method as usual with the application plugin
    mainClass = "com.example.Main"
}

dependencies {
    // implementation("...")
    // testImplementation("...")

    // black box test implementation configuration does not depend on implementation or testImplementation
    // You will most likely want JUnit and testcontainers dependencies here for starters
    // blackBoxTestImplementation("...")
}
```

And add a `Dockerfile` in the root of the subproject where this plugin is applied.
Note, the `my-service.tar` contains the applicatoin bundled using the `application` plugin and it
will be put in the Docker's context for the build.

For example:
```Dockerfile
FROM eclipse-temurin:17-jre
EXPOSE 8080
# Shouldn't need any more than this if not going for something heavyweight like Spring
ENV JAVA_OPTS -Xmx64m -Xms64m

# my-service is the name of the subproject where the plugin is applied
ENV SERVICE_NAME=my-service
ENTRYPOINT /opt/service/$SERVICE_NAME/bin/$SERVICE_NAME
# add the service archive last thing to utilize docker layer caching
ADD $SERVICE_NAME.tar /opt/service/
```

To build the service, use `./gradlew build` as usual. This will:
- compile the sources
- run the unit tests from the `test` source set
- build the docker image using the configured `docker` task
- run the `blackBoxTest` task using the sources in `blackBoxTest` source set

The plugin is also compatible with Gradle's [Configuration cache](https://docs.gradle.org/current/userguide/configuration_cache.html)
