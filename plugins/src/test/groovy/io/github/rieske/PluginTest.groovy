package io.github.rieske

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir


abstract class PluginTest extends Specification {
    @TempDir
    File testProjectDir

    File settingsFile
    File buildFile

    File file(String path) {
        return new File(testProjectDir, path)
    }

    def setup() {
        file('src/main/java').mkdirs()
        file('src/test/java').mkdirs()
        settingsFile = file('settings.gradle') << """
            rootProject.name = 'test'
            dependencyResolutionManagement {
                repositories {
                    mavenCentral() 
                } 
            }
        """
        buildFile = file('build.gradle')
    }

    BuildResult runTask(String task) {
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments(task, '--stacktrace')
                .withPluginClasspath()
                .build()
        println(result.output)
        return result
    }

    BuildResult runTaskWithFailure(String task) {
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments(task, '--stacktrace')
                .withPluginClasspath()
                .buildAndFail()
        println(result.output)
        return result
    }
}
