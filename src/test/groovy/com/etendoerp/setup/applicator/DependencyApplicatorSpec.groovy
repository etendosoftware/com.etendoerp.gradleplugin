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

    def "apply handles dependencies with version ranges"() {
        given:
        def buildFile = new File(tempDir.toFile(), 'build.gradle')
        buildFile.text = """
plugins {
    id 'java'
}

dependencies {
    implementation 'existing:lib:1.0.0'
}
"""
        
        def dependencies = [
            "implementation 'com.etendoerp:platform.extensions:[3.0.0,4.0.0)'",
            "implementation 'com.example:ranged:[1.0.0,2.0.0]'"
        ]

        when:
        DependencyApplicator.apply(project, dependencies)

        then:
        noExceptionThrown()
        def content = buildFile.text
        content.contains('[3.0.0,4.0.0)')
        content.contains('[1.0.0,2.0.0]')
    }

    def "apply handles dependencies with special regex characters"() {
        given:
        def buildFile = new File(tempDir.toFile(), 'build.gradle')
        buildFile.text = """
plugins {
    id 'java'
}

dependencies {
}
"""
        
        def dependencies = [
            "implementation 'com.example:lib-with-special(chars):1.0.0'",
            "implementation 'org.test:module+plus:2.0.0'",
            "implementation 'io.lib:with.dots:3.0.0'"
        ]

        when:
        DependencyApplicator.apply(project, dependencies)

        then:
        noExceptionThrown()
        def content = buildFile.text
        content.contains('lib-with-special(chars)')
        content.contains('module+plus')
        content.contains('with.dots')
    }

    def "apply detects existing dependencies with version ranges"() {
        given:
        def buildFile = new File(tempDir.toFile(), 'build.gradle')
        buildFile.text = """
plugins {
    id 'java'
}

dependencies {
    implementation 'com.etendoerp:platform.extensions:[3.0.0,4.0.0)'
}
"""
        
        def dependencies = [
            "implementation 'com.etendoerp:platform.extensions:[3.0.0,4.0.0)'"
        ]

        when:
        DependencyApplicator.apply(project, dependencies)

        then:
        noExceptionThrown()
        // Should detect it already exists and not add it again
    }

    def "apply handles parentheses in artifact names"() {
        given:
        def dependencies = [
            "implementation 'group:artifact(with-parens):1.0.0'"
        ]

        when:
        DependencyApplicator.apply(project, dependencies)

        then:
        noExceptionThrown()
    }

    def "apply handles asterisks in dependencies"() {
        given:
        def dependencies = [
            "implementation 'group:artifact-*:1.0.0'"
        ]

        when:
        DependencyApplicator.apply(project, dependencies)

        then:
        noExceptionThrown()
    }
}
