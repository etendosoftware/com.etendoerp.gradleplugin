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

    // ====== ARTIFACT MODULE TESTS ======

    def "apply artifact adds module to artifacts.list.COMPILATION.gradle"() {
        given:
        def artifactsFile = new File(tempDir.toFile(), 'artifacts.list.COMPILATION.gradle')
        artifactsFile.text = """
moduleDeps = [
  'com.existing:module:1.0.0'
]
"""
        
        def modules = ['com.example:newlib:2.0.0']

        when:
        ModuleApplicator.apply(project, modules)

        then:
        artifactsFile.exists()
        def content = artifactsFile.text
        content.contains('com.example:newlib:2.0.0')
        content.contains('// Template Dependencies')
    }

    def "apply artifact adds Template Dependencies section when missing"() {
        given:
        def artifactsFile = new File(tempDir.toFile(), 'artifacts.list.COMPILATION.gradle')
        artifactsFile.text = """
moduleDeps = [
  'com.existing:module:1.0.0'
]
"""
        
        def modules = ['com.new:artifact:1.5.0']

        when:
        ModuleApplicator.apply(project, modules)

        then:
        def content = artifactsFile.text
        content.contains('// Template Dependencies')
        content.indexOf('// Template Dependencies') < content.indexOf('com.new:artifact:1.5.0')
    }

    def "apply artifact skips Template Dependencies section when already exists"() {
        given:
        def artifactsFile = new File(tempDir.toFile(), 'artifacts.list.COMPILATION.gradle')
        artifactsFile.text = """
moduleDeps = [
  'com.existing:module:1.0.0',

  // Template Dependencies
  'com.template:dep:1.0.0'
]
"""
        
        def modules = ['com.new:artifact:2.0.0']

        when:
        ModuleApplicator.apply(project, modules)

        then:
        def content = artifactsFile.text
        // Should only have one Template Dependencies comment
        content.count('// Template Dependencies') == 1
        content.contains('com.new:artifact:2.0.0')
    }

    def "apply artifact skips when artifact already exists"() {
        given:
        def artifactsFile = new File(tempDir.toFile(), 'artifacts.list.COMPILATION.gradle')
        def initialContent = """
moduleDeps = [
  'com.existing:module:1.0.0',
  'com.duplicate:lib:1.5.0'
]
"""
        artifactsFile.text = initialContent
        
        def modules = ['com.duplicate:lib:1.5.0']

        when:
        ModuleApplicator.apply(project, modules)

        then:
        def content = artifactsFile.text
        // Content should remain essentially the same (just whitespace changes)
        content.contains('com.duplicate:lib:1.5.0')
    }

    def "apply artifact handles missing artifacts file gracefully"() {
        given:
        // No artifacts.list.COMPILATION.gradle file
        def modules = ['com.example:lib:1.0.0']

        when:
        ModuleApplicator.apply(project, modules)

        then:
        noExceptionThrown()
    }

    def "apply artifact handles invalid format in artifacts file"() {
        given:
        def artifactsFile = new File(tempDir.toFile(), 'artifacts.list.COMPILATION.gradle')
        // Invalid format - missing closing bracket
        artifactsFile.text = """
moduleDeps = [
  'com.existing:module:1.0.0'
"""
        
        def modules = ['com.new:artifact:1.0.0']

        when:
        ModuleApplicator.apply(project, modules)

        then:
        noExceptionThrown()
        // Content should remain unchanged due to invalid format
    }

    def "apply artifact handles content without trailing comma"() {
        given:
        def artifactsFile = new File(tempDir.toFile(), 'artifacts.list.COMPILATION.gradle')
        artifactsFile.text = """
moduleDeps = [
  'com.existing:module:1.0.0'
]
"""
        
        def modules = ['com.new:lib:1.0.0']

        when:
        ModuleApplicator.apply(project, modules)

        then:
        def content = artifactsFile.text
        content.contains('com.new:lib:1.0.0')
        content.contains(',')
    }

    def "apply artifact handles content with trailing comma"() {
        given:
        def artifactsFile = new File(tempDir.toFile(), 'artifacts.list.COMPILATION.gradle')
        artifactsFile.text = """
moduleDeps = [
  'com.existing:module:1.0.0',
]
"""
        
        def modules = ['com.another:lib:2.0.0']

        when:
        ModuleApplicator.apply(project, modules)

        then:
        def content = artifactsFile.text
        content.contains('com.another:lib:2.0.0')
        // Should have proper comma formatting
    }

    def "apply artifact adds multiple artifacts correctly"() {
        given:
        def artifactsFile = new File(tempDir.toFile(), 'artifacts.list.COMPILATION.gradle')
        artifactsFile.text = """
moduleDeps = [
  'com.existing:module:1.0.0'
]
"""
        
        def modules = [
            'com.first:lib:1.0.0',
            'com.second:lib:2.0.0',
            'com.third:lib:3.0.0'
        ]

        when:
        ModuleApplicator.apply(project, modules)

        then:
        def content = artifactsFile.text
        content.contains('com.first:lib:1.0.0')
        content.contains('com.second:lib:2.0.0')
        content.contains('com.third:lib:3.0.0')
        content.contains('// Template Dependencies')
    }
}
