package com.etendoerp.automation

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class DockerServiceSpec extends Specification {

    def project
    def service

    def setup() {
        project = ProjectBuilder.builder().build()
        service = new DockerService(project, "et-test")
        ArrayList.metaClass.execute = { ->
            def joined = delegate.join(' ')
            if (joined.contains("ps -q missing")) {
                return new FakeProcess("", "", 0)
            }
            if (joined.contains("inspect")) {
                return new FakeProcess("details", "", 0)
            }
            if (joined.contains("restart backend")) {
                return new FakeProcess("ok", "", 0)
            }
            // Default for listContainers and other ps commands
            return new FakeProcess("container-one\ncontainer-two\n", "", 0)
        }
    }

    def cleanup() {
        GroovySystem.metaClassRegistry.removeMetaClass(ArrayList)
    }

    def "listContainers builds docker compose ps command"() {
        when:
        def result = service.listContainers()

        then:
        result.success
        result.command.contains("docker compose -p et-test ps -a")
        result.output.contains("container-one")
    }

    def "inspectContainer returns friendly message when container id missing"() {
        when:
        def result = service.inspectContainer("missing")

        then:
        !result.success
        result.error == "Container missing not found or not running"
    }

    def "executeCustomCommand proxies arbitrary command strings"() {
        when:
        def result = service.executeCustomCommand("docker compose -p et-test restart backend")

        then:
        result.success
        result.command == "docker compose -p et-test restart backend"
    }

    private static class FakeProcess extends Process {
        private final InputStream stdout
        private final InputStream stderr
        private final OutputStream stdin = new ByteArrayOutputStream()
        private final int code

        FakeProcess(String out, String err, int exitCode) {
            this.stdout = new ByteArrayInputStream(out.getBytes())
            this.stderr = new ByteArrayInputStream(err.getBytes())
            this.code = exitCode
        }

        @Override
        OutputStream getOutputStream() { stdin }

        @Override
        InputStream getInputStream() { stdout }

        @Override
        InputStream getErrorStream() { stderr }

        @Override
        int waitFor() { code }

        @Override
        int exitValue() { code }

        @Override
        void destroy() {}
    }
}
