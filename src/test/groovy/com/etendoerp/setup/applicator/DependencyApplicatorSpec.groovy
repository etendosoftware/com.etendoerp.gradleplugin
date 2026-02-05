package com.etendoerp.setup.applicator

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Tests for DependencyApplicator
 */
class DependencyApplicatorSpec extends Specification {

    @TempDir
    Path tempDir

    def project

    def setup() {
        project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()
                
        // Create a build.gradle file as required by DependencyApplicator
        def buildFile = new File(tempDir.toFile(), 'build.gradle')
        buildFile.text = """
plugins {
    id 'java'
}

dependencies {
}
"""
    }

    // ====== APPLY DEPENDENCIES TESTS ======

    def "apply adds dependencies to build.gradle"() {
        given:
        def dependencies = [
            'com.example:library:1.0.0',
            'org.test:module:2.0.0'
        ]

        when:
        DependencyApplicator.apply(project, dependencies)

        then:
        def buildFile = new File(tempDir.toFile(), 'build.gradle')
        buildFile.exists()
        noExceptionThrown()
    }

    def "apply handles empty dependencies list"() {
        when:
        DependencyApplicator.apply(project, [])

        then:
        noExceptionThrown()
    }

    def "apply handles null dependencies"() {
        when:
        DependencyApplicator.apply(project, null)

        then:
        noExceptionThrown()
    }

    def "apply throws exception when build.gradle does not exist"() {
        given:
        def newProject = ProjectBuilder.builder()
                .withProjectDir(new File(tempDir.toFile(), 'nobuild'))
                .build()
        new File(tempDir.toFile(), 'nobuild').mkdirs()
        
        def dependencies = ['com.example:library:1.0.0']

        when:
        DependencyApplicator.apply(newProject, dependencies)

        then:
        thrown(FileNotFoundException)
    }
}
