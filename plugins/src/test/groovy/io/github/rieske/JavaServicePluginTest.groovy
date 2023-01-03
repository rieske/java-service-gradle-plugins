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
        """
    }

    def givenDockerfileExists() {
        dockerfile << """
            FROM busybox
        """
    }

    def givenGoodSourceAndBlackBoxTestsExist() {
        file("src/main/java/Foo.java") << """
            class Foo {
            }
        """
        file("src/blackBoxTest/java/FooTest.java") << """
            class FooTest {
                void foo() {
                } 
            }
        """
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
        buildFile << """
            dependencies {
                blackBoxTestImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
            }
        """
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
        file("src/main/java/Foo.java").setText("""
            class Foo {
                void bar() {}
            }
        """)
        def result = runTask("blackBoxTest")

        then:
        result.task(":blackBoxTest").outcome == TaskOutcome.SUCCESS
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
}
