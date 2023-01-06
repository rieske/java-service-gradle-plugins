package io.github.rieske

import org.gradle.testkit.runner.TaskOutcome

class JavaServicePluginTest extends PluginTest {
    File dockerfile

    def setup() {
        dockerfile = file('Dockerfile')
        file('src/blackBoxTest/java').mkdirs()
        buildFile << """
            plugins {
                id("io.github.rieske.java-service") 
            }
            application {
                mainClass = "Main"
            }
            dependencies {
                implementation("com.sparkjava:spark-core:2.9.4")
                blackBoxTestImplementation("org.testcontainers:testcontainers:1.17.6")
                blackBoxTestImplementation("io.rest-assured:rest-assured:5.3.0")
            }
        """
    }

    def givenDockerfileExists() {
        dockerfile << """
            FROM eclipse-temurin:17-jre
            EXPOSE 8080
            ENV JAVA_OPTS -Xmx64m -Xms64m
            ENV SERVICE_NAME=test-service
            ENTRYPOINT /opt/service/\$SERVICE_NAME/bin/\$SERVICE_NAME
            ADD \$SERVICE_NAME.tar /opt/service/
        """
    }

    def givenGoodSourceAndBlackBoxTestsExist() {
        file("src/main/java/Main.java") << mainWithEndpointResponding("Hello from service!")
        file("src/blackBoxTest/java/ServiceTest.java") << blackBoxTestAssertingEndpoint("Hello from service!")
    }

    def givenConfigurationCacheIsEnabled() {
        file("gradle.properties") << "org.gradle.unsafe.configuration-cache=true"
    }

    def "uses JUnit platform for unit tests"() {
        given:
        buildFile << """
            dependencies {
                testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
            }
        """
        file("src/test/java/FooTest.java") << """
            class FooTest {
                @org.junit.jupiter.api.Test
                void foo() {
                    throw new RuntimeException();
                } 
            }
        """

        when:
        def result = runTaskWithFailure("test")

        then:
        result.task(":test").outcome == TaskOutcome.FAILED
        // make sure we log the stack trace on test failures
        result.output.contains("at FooTest.foo(FooTest.java:")
    }

    def "adds blackBoxTest task and hooks it to the check lifecycle task"() {
        given:
        givenDockerfileExists()

        when:
        def result = runTask("check")

        then:
        result.task(":docker").outcome == TaskOutcome.SUCCESS
        result.task(":blackBoxTest").outcome == TaskOutcome.NO_SOURCE
    }

    def "uses JUnit Platform for black box tests"() {
        given:
        givenDockerfileExists()
        file("src/blackBoxTest/java/FooTest.java") << """
            class FooTest {
                @org.junit.jupiter.api.Test
                void foo() {
                    throw new RuntimeException();
                } 
            }
        """

        when:
        def result = runTaskWithFailure("check")

        then:
        result.task(":docker").outcome == TaskOutcome.SUCCESS
        result.task(":blackBoxTest").outcome == TaskOutcome.FAILED
        // make sure we log the stack trace on test failures
        result.output.contains("at FooTest.foo(FooTest.java:")
    }

    def "blackBoxTest task depends on the service's docker image"() {
        when:
        def result = runTaskWithFailure("check")

        then:
        result.task(":docker").outcome == TaskOutcome.FAILED
        result.output.contains("> Dockerfile does not exist")
        result.task(":blackBoxTest") == null
    }

    def "black box tests can not access production source code"() {
        given:
        givenDockerfileExists()
        file("src/main/java/Foo.java") << """
            class Foo {
            }
        """
        file("src/blackBoxTest/java/FooTest.java") << """
            class FooTest {
                void foo() {
                    new Foo();
                } 
            }
        """

        when:
        def result = runTaskWithFailure("check")

        then:
        result.task(":compileBlackBoxTestJava").outcome == TaskOutcome.FAILED
        result.output.contains("symbol:   class Foo")
    }

    def "caches black box test results"() {
        given:
        givenDockerfileExists()
        givenGoodSourceAndBlackBoxTestsExist()
        runTask("blackBoxTest")

        when:
        def result = runTask("blackBoxTest")

        then:
        result.task(":blackBoxTest").outcome == TaskOutcome.UP_TO_DATE
    }

    def "reruns black box tests when dockerfile changes"() {
        given:
        givenDockerfileExists()
        givenGoodSourceAndBlackBoxTestsExist()
        runTask("blackBoxTest")

        when:
        dockerfile << """
            RUN echo foo
        """
        def result = runTask("blackBoxTest")

        then:
        result.task(":blackBoxTest").outcome == TaskOutcome.SUCCESS
    }

    def "reruns black box tests when production code changes"() {
        given:
        givenDockerfileExists()
        givenGoodSourceAndBlackBoxTestsExist()
        runTask("blackBoxTest")

        when:
        file("src/main/java/Main.java").setText(mainWithEndpointResponding("Changed message"))
        def result = runTaskWithFailure("blackBoxTest")

        then:
        result.task(":blackBoxTest").outcome == TaskOutcome.FAILED
        result.output.contains("Expected: \"Hello from service!\"")
        result.output.contains("Actual: Changed message")
    }

    def "stores configuration cache"() {
        given:
        givenDockerfileExists()
        givenConfigurationCacheIsEnabled()
        givenGoodSourceAndBlackBoxTestsExist()

        when:
        def result = runTask("build")

        then:
        result.task(":blackBoxTest").outcome == TaskOutcome.SUCCESS
        result.task(":build").outcome == TaskOutcome.SUCCESS
        result.output.contains("Calculating task graph as no configuration cache is available for tasks: build")
        result.output.contains("Configuration cache entry stored.")
    }

    def "reuses configuration cache"() {
        given:
        givenDockerfileExists()
        givenConfigurationCacheIsEnabled()
        givenGoodSourceAndBlackBoxTestsExist()
        runTask("build")

        when:
        def result = runTask("build")

        then:
        result.task(":blackBoxTest").outcome == TaskOutcome.UP_TO_DATE
        result.task(":build").outcome == TaskOutcome.UP_TO_DATE
        result.output.contains("Reusing configuration cache.")
        result.output.contains("Configuration cache entry reused.")
    }

    private String mainWithEndpointResponding(String expectedMessage) {
        return """
            public class Main {
                public static void main(String[] args) {
                    spark.Spark.port(8080);
                    spark.Spark.get("/test", (request, response) -> "$expectedMessage");
                    spark.Spark.awaitInitialization();
                }
            }
        """
    }

    private String blackBoxTestAssertingEndpoint(String expectedMessage) {
        return """
            import io.restassured.RestAssured;
            import org.hamcrest.Matchers;
            import org.junit.jupiter.api.Test;
            import org.testcontainers.containers.GenericContainer;
            import org.testcontainers.containers.wait.strategy.Wait;
            import org.testcontainers.utility.DockerImageName;

            class ServiceTest {
                @Test
                void serviceStarts() {
                    try (var container = new GenericContainer<>(DockerImageName.parse("test-service:snapshot"))) {
                        container.withExposedPorts(8080).waitingFor(Wait.forListeningPort()).start();
                        RestAssured.when().get("http://%s:%s/test".formatted(container.getHost(), container.getMappedPort(8080)))
                                .then().body(Matchers.equalTo("$expectedMessage"));
                    }
                }
            }
        """
    }
}
