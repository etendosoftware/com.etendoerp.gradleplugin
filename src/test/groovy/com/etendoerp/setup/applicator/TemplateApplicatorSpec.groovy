package com.etendoerp.setup.applicator

import com.etendoerp.setup.template.Template
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Tests for TemplateApplicator
 */
class TemplateApplicatorSpec extends Specification {

    @TempDir
    Path tempDir

    def project

    def setup() {
        project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()
                
        // Create build.gradle as required
        def buildFile = new File(tempDir.toFile(), 'build.gradle')
        buildFile.text = """
plugins {
    id 'java'
}

dependencies {
}
"""
    }

    // ====== TEMPLATE APPLICATION TESTS ======

    def "apply processes template with properties"() {
        given:
        def template = new Template(
            name: 'test-template',
            properties: [
                'test.property': 'value1',
                'another.property': 'value2'
            ]
        )

        when:
        TemplateApplicator.apply(project, template)

        then:
        def propsFile = new File(tempDir.toFile(), 'gradle.properties')
        propsFile.exists()
        propsFile.text.contains('test.property')
    }

    def "apply processes template with dependencies"() {
        given:
        def template = new Template(
            name: 'deps-template',
            dependencies: [
                'com.example:library:1.0.0'
            ]
        )

        when:
        TemplateApplicator.apply(project, template)

        then:
        noExceptionThrown()
    }

    def "apply processes template with modules"() {
        given:
        def template = new Template(
            name: 'modules-template',
            modules: [
                'com.example:module:1.0.0'
            ]
        )

        when:
        TemplateApplicator.apply(project, template)

        then:
        noExceptionThrown()
    }

    def "apply processes complete template with all sections"() {
        given:
        def template = new Template(
            name: 'complete-template',
            properties: ['prop': 'value'],
            dependencies: ['com.example:lib:1.0.0'],
            modules: ['com.example:module:1.0.0']
        )

        when:
        TemplateApplicator.apply(project, template)

        then:
        noExceptionThrown()
    }

    def "apply creates backups directory"() {
        given:
        def propsFile = new File(tempDir.toFile(), 'gradle.properties')
        propsFile.text = "existing=value\n"
        
        def template = new Template(
            name: 'backup-test',
            properties: ['new': 'value']
        )

        when:
        TemplateApplicator.apply(project, template)

        then:
        def backupDir = new File(tempDir.toFile(), '.template-backups')
        backupDir.exists()
        backupDir.isDirectory()
    }

    def "apply handles template with empty sections"() {
        given:
        def template = new Template(
            name: 'empty-template',
            properties: [:],
            dependencies: [],
            modules: []
        )

        when:
        TemplateApplicator.apply(project, template)

        then:
        noExceptionThrown()
    }

    def "apply handles template with null sections"() {
        given:
        def template = new Template(
            name: 'null-template',
            properties: null,
            dependencies: null,
            modules: null
        )

        when:
        TemplateApplicator.apply(project, template)

        then:
        noExceptionThrown()
    }
}
