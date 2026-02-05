package com.etendoerp.setup

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Basic tests for SetupApplyTemplatesTask
 */
class SetupApplyTemplatesTaskSpec extends Specification {

    @TempDir
    Path tempDir

    def project
    def task

    def setup() {
        project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()

        task = project.tasks.create('testApplyTemplates', SetupApplyTemplatesTask)
    }

    // ====== TASK CONFIGURATION TESTS ======

    def "task is configured correctly"() {
        expect:
        task.group == 'setup'
        task.description == 'Apply configuration templates to the project'
    }

    def "task has null default values for options"() {
        expect:
        task.template == null
        task.file == null
        task.url == null
    }

    def "task allows setting template option"() {
        when:
        task.template = 'copilot'

        then:
        task.template == 'copilot'
    }

    def "task allows setting file option"() {
        when:
        task.file = '/path/to/template.yml'

        then:
        task.file == '/path/to/template.yml'
    }

    def "task allows setting url option"() {
        when:
        task.url = 'https://example.com/template.yml'

        then:
        task.url == 'https://example.com/template.yml'
    }

    // ====== EXECUTE METHOD TESTS ======

    def "execute fails when no template source is provided"() {
        when:
        task.execute()

        then:
        thrown(IllegalArgumentException)
    }

    def "execute fails when template file does not exist"() {
        given:
        task.file = '/non/existent/file.template'

        when:
        task.execute()

        then:
        thrown(FileNotFoundException)
    }

    def "execute applies template from file successfully"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'test.template')
        templateFile.text = """
[properties]
test.property=testValue

[dependencies]
com.example:lib:1.0.0
"""
        
        def gradlePropsFile = new File(tempDir.toFile(), 'gradle.properties')
        gradlePropsFile.text = "# existing properties\n"

        def buildFile = new File(tempDir.toFile(), 'build.gradle')
        buildFile.text = """
plugins {
    id 'java'
}

dependencies {
}
"""
        
        task.file = templateFile.absolutePath

        when:
        task.execute()

        then:
        notThrown(Exception)
        gradlePropsFile.exists()
        gradlePropsFile.text.contains('test.property=testValue')
    }

    def "execute handles template with only properties section"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'props-only.template')
        templateFile.text = """
[properties]
prop1=value1
prop2=value2
"""
        
        def gradlePropsFile = new File(tempDir.toFile(), 'gradle.properties')
        gradlePropsFile.text = ""

        task.file = templateFile.absolutePath

        when:
        task.execute()

        then:
        notThrown(Exception)
        gradlePropsFile.text.contains('prop1=value1')
        gradlePropsFile.text.contains('prop2=value2')
    }

    def "execute logs error and rethrows exception on failure"() {
        given:
        task.file = '/invalid/path/template'

        when:
        task.execute()

        then:
        def e = thrown(FileNotFoundException)
        e.message.contains('invalid')
    }
}
