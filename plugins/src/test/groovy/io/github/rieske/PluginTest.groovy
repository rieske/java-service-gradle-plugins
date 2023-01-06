package io.github.rieske

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import spock.lang.Specification
import spock.lang.TempDir


abstract class PluginTest extends Specification {

    String gradleVersion = GradleVersion.current().version

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
            rootProject.name = 'test-service'
            dependencyResolutionManagement {
                repositories {
                    mavenCentral() 
                } 
            }
        """
        buildFile = file('build.gradle')
    }

    BuildResult runTask(String task) {
        def result = makeGradleRunner(task).build()
        println(result.output)
        return result
    }

    BuildResult runTaskWithFailure(String task) {
        def result = makeGradleRunner(task).buildAndFail()
        println(result.output)
        return result
    }

    private GradleRunner makeGradleRunner(String task) {
        return GradleRunner.create()
                .withGradleVersion(gradleVersion)
                .withProjectDir(testProjectDir)
                .withArguments(task, '--stacktrace')
                .withPluginClasspath()
    }
}
