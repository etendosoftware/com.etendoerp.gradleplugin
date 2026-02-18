package com.etendoerp.automation

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class DockerServiceSpec extends Specification {

    def project
    def service

    def setup() {
        project = ProjectBuilder.builder().build()
        service = new DockerService(project, "et-test")
    }

    def cleanup() {
    }

    def "listContainers builds docker compose ps command"() {
        given:
        service.metaClass.executeDockerCommand = { List<String> cmd, String desc ->
            return [success: true, output: "", error: "", command: cmd.join(' '), exitCode: 0, description: desc]
        }

        when:
        def result = service.listContainers()

        then:
        result.success
        result.command.contains("docker compose -p et-test ps -a")
        result.output != null
    }

    def "inspectContainer returns friendly message when container id missing"() {
        given:
        service.metaClass.executeDockerCommand = { List<String> cmd, String desc ->
            return [success: true, output: "", error: "", command: cmd.join(' '), exitCode: 0, description: desc]
        }

        when:
        def result = service.inspectContainer("missing")

        then:
        !result.success
        result.error == "Container missing not found or not running"
    }

    def "executeCustomCommand proxies arbitrary command strings"() {
        given:
        service.metaClass.executeDockerCommand = { List<String> cmd, String desc ->
            return [success: true, output: "", error: "", command: cmd.join(' '), exitCode: 0, description: desc]
        }

        when:
        def result = service.executeCustomCommand("docker compose -p et-test restart backend")

        then:
        result.command == "docker compose -p et-test restart backend"
    }
}
