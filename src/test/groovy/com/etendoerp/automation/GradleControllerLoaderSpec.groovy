package com.etendoerp.automation

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import spock.lang.Specification
import spock.lang.TempDir

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
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
        GroovySystem.metaClassRegistry.removeMetaClass(Runtime)
        GroovySystem.metaClassRegistry.removeMetaClass(System)
        GroovySystem.metaClassRegistry.removeMetaClass(Thread)
        GroovySystem.metaClassRegistry.removeMetaClass(io.javalin.Javalin)
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

    def "load registers setup.web task"() {
        when:
        GradleControllerLoader.load(project)

        then:
        def task = project.tasks.findByName("setup.web")
        task != null
        task.group == "setup"
        task.description == "Starts the Gradle Control Web UI on port 3851"
    }

    def "openBrowser uses platform specific command"() {
        given:
        def runtime = Mock(Runtime)
        GroovyMock(Runtime, global: true)
        Runtime.getRuntime() >> runtime

        when: "macOS"
        System.metaClass.'static'.getProperty = { String key ->
            key == "os.name" ? "Mac OS X" : null
        }
        GradleControllerLoader.openBrowser("http://localhost:3851", project)

        then:
        1 * runtime.exec("open http://localhost:3851")

        when: "Linux"
        System.metaClass.'static'.getProperty = { String key ->
            key == "os.name" ? "Linux" : null
        }
        GradleControllerLoader.openBrowser("http://localhost:3851", project)

        then:
        1 * runtime.exec("xdg-open http://localhost:3851")

        when: "Windows"
        System.metaClass.'static'.getProperty = { String key ->
            key == "os.name" ? "Windows 11" : null
        }
        GradleControllerLoader.openBrowser("http://localhost:3851", project)

        then:
        1 * runtime.exec("rundll32 url.dll,FileProtocolHandler http://localhost:3851")
    }

    def "startGradleControllerServer registers endpoints and shuts down on interrupt"() {
        given:
        def app = Stub(io.javalin.Javalin)
        def stopped = false
        GroovyMock(io.javalin.Javalin, global: true)
        io.javalin.Javalin.create(_ as Closure) >> app
        app.start(3851) >> app
        app.metaClass.get = { Object... args -> app }
        app.metaClass.post = { Object... args -> app }
        app.stop() >> { stopped = true }

        def runtime = Mock(Runtime)
        GroovyMock(Runtime, global: true)
        Runtime.getRuntime() >> runtime
        System.metaClass.'static'.getProperty = { String key ->
            key == "os.name" ? "Linux" : null
        }
        1 * runtime.exec("xdg-open http://localhost:3851")

        def fakeThread = Mock(Thread)
        Thread.metaClass.static.currentThread = { -> fakeThread }
        fakeThread.join() >> { throw new InterruptedException("stop") }

        when:
        GradleControllerLoader.startGradleControllerServer(project)

        then:
        stopped
        1 * runtime.addShutdownHook(_ as Thread)
    }

    def "proxyToBackend forwards response and headers"() {
        given:
        def server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext("/sws/login") { exchange ->
            exchange.getResponseHeaders().add("x-back", "v1")
            byte[] body = "ok".bytes
            exchange.sendResponseHeaders(200, body.length)
            exchange.responseBody.withCloseable { it.write(body) }
        }
        server.start()
        def baseUrl = "http://localhost:${server.address.port}"

        def ctx = Mock(io.javalin.http.Context)
        ctx.queryString() >> null
        ctx.path() >> "/sws/login"
        ctx.bodyAsBytes() >> "payload".bytes
        ctx.method() >> io.javalin.http.HandlerType.POST
        ctx.headerMap() >> ["x-test": "1", "content-length": "4"]
        ctx.status(_ as int) >> ctx

        when:
        GradleControllerLoader.proxyToBackend(ctx, project, baseUrl)

        then:
        1 * ctx.header("x-back", "v1")
        1 * ctx.status(200)
        1 * ctx.result(_ as ByteArrayInputStream)

        cleanup:
        server.stop(0)
    }

    def "proxyToBackend returns 502 on exception"() {
        given:
        def holder = [:]
        def ctx = [
                queryString: { -> null },
                path: { -> "/sws/login" },
                bodyAsBytes: { -> new byte[0] },
                method: { -> io.javalin.http.HandlerType.GET },
                headerMap: { -> [:] },
                status: { Object... args -> holder.ctx },
                json: { Object... args -> holder.ctx }
        ] as io.javalin.http.Context
        holder.ctx = ctx

        when:
        GradleControllerLoader.proxyToBackend(ctx, project, "http://bad uri")

        then:
        noExceptionThrown()
    }
}
