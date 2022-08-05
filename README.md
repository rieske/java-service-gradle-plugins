# Gradle plugins for building Java services

WIP

## io.github.rieske.java-service

Configures a reproducible build that packages the Java application (using Gradle's core `application` plugin)
in a tar file and onwards into a Docker image (using Palantir's [`docker`](https://github.com/palantir/gradle-docker) plugin).

Configures `blackBoxTest` source set that depends on the service's docker image.
This allows to test the produced artifact before it is published/deployed.

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
