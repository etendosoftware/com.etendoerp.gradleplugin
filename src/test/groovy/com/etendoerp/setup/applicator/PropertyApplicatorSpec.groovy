package com.etendoerp.setup.applicator

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Tests for PropertyApplicator
 */
class PropertyApplicatorSpec extends Specification {

    @TempDir
    Path tempDir

    def project

    def setup() {
        project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()
    }

    // ====== APPLY PROPERTIES TESTS ======

    def "apply creates gradle.properties if not exists"() {
        given:
        def properties = [
            'test.property': 'value1',
            'another.property': 'value2'
        ]

        when:
        PropertyApplicator.apply(project, properties)

        then:
        def propsFile = new File(tempDir.toFile(), 'gradle.properties')
        propsFile.exists()
        def content = propsFile.text
        content.contains('test.property')
        content.contains('another.property')
    }

    def "apply adds properties to existing gradle.properties"() {
        given:
        def propsFile = new File(tempDir.toFile(), 'gradle.properties')
        propsFile.text = "existing.property=oldvalue\n"
        
        def properties = [
            'new.property': 'newvalue'
        ]

        when:
        PropertyApplicator.apply(project, properties)

        then:
        def content = propsFile.text
        content.contains('existing.property=oldvalue')
        content.contains('new.property=newvalue')
    }

    def "apply handles empty properties map"() {
        when:
        PropertyApplicator.apply(project, [:])

        then:
        noExceptionThrown()
    }

    def "apply handles null properties"() {
        when:
        PropertyApplicator.apply(project, null)

        then:
        noExceptionThrown()
    }

    def "apply updates existing property values"() {
        given:
        def propsFile = new File(tempDir.toFile(), 'gradle.properties')
        propsFile.text = "test.property=oldvalue\n"
        
        def properties = [
            'test.property': 'newvalue'
        ]

        when:
        PropertyApplicator.apply(project, properties)

        then:
        def content = propsFile.text
        content.contains('test.property')
        content.contains('newvalue')
    }
}
