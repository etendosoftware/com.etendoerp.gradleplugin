package com.etendoerp.setup.applicator

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Tests for ModuleApplicator
 */
class ModuleApplicatorSpec extends Specification {

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

    // ====== MODULE APPLICATION TESTS ======

    def "apply handles empty module list"() {
        when:
        ModuleApplicator.apply(project, [])

        then:
        noExceptionThrown()
    }

    def "apply handles null module list"() {
        when:
        ModuleApplicator.apply(project, null)

        then:
        noExceptionThrown()
    }

    def "apply processes artifact modules"() {
        given:
        def modules = [
            'com.example:library:1.0.0',
            'org.test:module:2.0.0'
        ]

        when:
        ModuleApplicator.apply(project, modules)

        then:
        noExceptionThrown()
    }

    def "apply creates modules directory for git modules"() {
        given:
        def modules = [
            'git::https://github.com/user/repo.git::branch=main'
        ]

        when:
        ModuleApplicator.apply(project, modules)

        then:
        // Git clone will fail in test environment, that's expected
        thrown(Exception)
    }

    def "apply handles git module without branch"() {
        given:
        def modules = [
            'git::https://github.com/user/repo.git'
        ]

        when:
        ModuleApplicator.apply(project, modules)

        then:
        // Git clone will fail in test environment, that's expected
        thrown(Exception)
    }

    def "apply handles invalid git module format"() {
        given:
        def modules = [
            'git::invalid-format'
        ]

        when:
        ModuleApplicator.apply(project, modules)

        then:
        // Should throw exception for invalid format
        thrown(Exception)
    }

    def "apply skips already cloned git modules"() {
        given:
        // Pre-create modules directory with a module
        def modulesDir = new File(tempDir.toFile(), 'modules')
        modulesDir.mkdirs()
        def existingModule = new File(modulesDir, 'repo')
        existingModule.mkdirs()
        
        def modules = [
            'git::https://github.com/user/repo.git::branch=main'
        ]

        when:
        ModuleApplicator.apply(project, modules)

        then:
        noExceptionThrown()
        existingModule.exists()
    }

    def "apply processes mixed artifact and git modules"() {
        given:
        // Pre-create the module directory so git clone is skipped
        def modulesDir = new File(tempDir.toFile(), 'modules')
        modulesDir.mkdirs()
        def repoDir = new File(modulesDir, 'repo')
        repoDir.mkdirs()
        
        def modules = [
            'com.example:library:1.0.0',
            'git::https://github.com/user/repo.git::branch=develop'
        ]

        when:
        ModuleApplicator.apply(project, modules)

        then:
        noExceptionThrown()
    }
}
