package com.etendoerp.automation

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import spock.lang.Specification
import spock.lang.TempDir

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Path

class GradleControllerLoaderSpec extends Specification {

    @TempDir
    Path tempDir

    def project

    def setup() {
        project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()
    }

    def cleanup() {
        GroovySystem.metaClassRegistry.removeMetaClass(GradleConnector)
    }

    def "executeGradleTask returns output on success"() {
        given:
        def connector = Stub(GradleConnector)
        def connection = Mock(ProjectConnection)
        def launcher = Mock(BuildLauncher)

        ByteArrayOutputStream capturedOut
        ByteArrayOutputStream capturedErr

        GradleConnector.metaClass.'static'.newConnector = { -> connector }
        connector.forProjectDirectory(_) >> connector
        connector.connect() >> connection
        connection.newBuild() >> launcher
        launcher.forTasks(_) >> launcher
        launcher.setStandardOutput(_ as OutputStream) >> { OutputStream os ->
            capturedOut = os
            return launcher
        }
        launcher.setStandardError(_ as OutputStream) >> { OutputStream os ->
            capturedErr = os
            return launcher
        }
        launcher.run() >> {
            capturedOut?.write("build-ok".bytes)
            capturedErr?.write("warn".bytes)
        }

        when:
        def result = GradleControllerLoader.executeGradleTask(project, "testTask")

        then:
        result.success
        result.output.contains("build-ok")
        result.error == ""
        1 * connection.close()
    }

    def "executeGradleTask captures failure and still closes connection"() {
        given:
        def connector = Stub(GradleConnector)
        def connection = Mock(ProjectConnection)
        def launcher = Mock(BuildLauncher)

        GradleConnector.metaClass.'static'.newConnector = { -> connector }
        connector.forProjectDirectory(_) >> connector
        connector.connect() >> connection
        connection.newBuild() >> launcher
        launcher.forTasks(_) >> launcher
        launcher.setStandardOutput(_ as OutputStream) >> launcher
        launcher.setStandardError(_ as OutputStream) >> launcher
        launcher.run() >> { throw new RuntimeException("boom") }

        when:
        def result = GradleControllerLoader.executeGradleTask(project, "failingTask")

        then:
        !result.success
        result.error == "exit status 1"
        1 * connection.close()
    }
}
