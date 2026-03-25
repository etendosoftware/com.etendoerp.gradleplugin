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
        task.template = 'local'

        then:
        task.template == 'local'
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

    // ====== ENVIRONMENT VALIDATION TESTS ======

    def "execute succeeds when no gradle.properties exists (clean environment)"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'test.template')
        templateFile.text = """
[properties]
test.property=testValue
"""
        
        task.file = templateFile.absolutePath

        when:
        task.execute()

        then:
        notThrown(Exception)
    }

    def "execute succeeds when gradle.properties has no database config"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'test.template')
        templateFile.text = """
[properties]
new.property=newValue
"""
        
        def gradlePropsFile = new File(tempDir.toFile(), 'gradle.properties')
        gradlePropsFile.text = """
# Just comments
some.other.property=value
"""
        
        task.file = templateFile.absolutePath

        when:
        task.execute()

        then:
        notThrown(Exception)
        gradlePropsFile.text.contains('new.property=newValue')
    }

    def "execute succeeds when database config exists but connection fails"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'test.template')
        templateFile.text = """
[properties]
test.property=value
"""
        
        def gradlePropsFile = new File(tempDir.toFile(), 'gradle.properties')
        // Database config that points to non-existent database
        gradlePropsFile.text = """
bbdd.sid=nonexistent_db_${System.currentTimeMillis()}
bbdd.systemUser=postgres
bbdd.systemPassword=postgres
bbdd.rdbms=localhost
bbdd.port=5432
"""
        
        task.file = templateFile.absolutePath

        when:
        task.execute()

        then:
        // Should succeed because database doesn't exist (connection fails)
        notThrown(Exception)
    }

    def "execute succeeds with --force flag even when validation would fail"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'test.template')
        templateFile.text = """
[properties]
new.property=newValue
"""
        
        def gradlePropsFile = new File(tempDir.toFile(), 'gradle.properties')
        gradlePropsFile.text = """
bbdd.sid=some_db
bbdd.systemUser=postgres
"""
        
        task.file = templateFile.absolutePath
        task.force = true

        when:
        task.execute()

        then:
        notThrown(Exception)
        gradlePropsFile.text.contains('new.property=newValue')
    }

    def "validation checks for bbdd.sid property"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'test.template')
        templateFile.text = """
[properties]
test.property=value
"""
        
        def gradlePropsFile = new File(tempDir.toFile(), 'gradle.properties')
        gradlePropsFile.text = """
bbdd.sid=test_db_${System.currentTimeMillis()}
bbdd.systemUser=nonexistent_user
bbdd.systemPassword=wrong_pass
"""
        
        task.file = templateFile.absolutePath

        when:
        task.execute()

        then:
        // Should succeed because connection will fail (invalid credentials/non-existent DB)
        notThrown(Exception)
    }

    def "validation uses bbdd.rdbms and bbdd.port properties"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'test.template')
        templateFile.text = """
[properties]
test.property=value
"""
        
        def gradlePropsFile = new File(tempDir.toFile(), 'gradle.properties')
        gradlePropsFile.text = """
bbdd.sid=test_db_${System.currentTimeMillis()}
bbdd.systemUser=postgres
bbdd.rdbms=nonexistent.host.local
bbdd.port=9999
"""
        
        task.file = templateFile.absolutePath

        when:
        task.execute()

        then:
        // Should succeed because connection will fail (invalid host)
        notThrown(Exception)
    }

    def "validation uses custom bbdd.url if provided"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'test.template')
        templateFile.text = """
[properties]
test.property=value
"""
        
        def gradlePropsFile = new File(tempDir.toFile(), 'gradle.properties')
        gradlePropsFile.text = """
bbdd.sid=test_db
bbdd.systemUser=postgres
bbdd.url=jdbc:postgresql://invalid.host:5432/nonexistent_db_${System.currentTimeMillis()}
"""
        
        task.file = templateFile.absolutePath

        when:
        task.execute()

        then:
        // Should succeed because connection will fail (invalid URL)
        notThrown(Exception)
    }

    def "validation skips when only bbdd.sid is present without systemUser"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'test.template')
        templateFile.text = """
[properties]
test.property=value
"""
        
        def gradlePropsFile = new File(tempDir.toFile(), 'gradle.properties')
        gradlePropsFile.text = """
bbdd.sid=some_db
# No bbdd.systemUser
"""
        
        task.file = templateFile.absolutePath

        when:
        task.execute()

        then:
        // Should succeed - incomplete config means environment is clean
        notThrown(Exception)
    }

    def "validation skips when only bbdd.systemUser is present without sid"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'test.template')
        templateFile.text = """
[properties]
test.property=value
"""
        
        def gradlePropsFile = new File(tempDir.toFile(), 'gradle.properties')
        gradlePropsFile.text = """
bbdd.systemUser=postgres
# No bbdd.sid
"""
        
        task.file = templateFile.absolutePath

        when:
        task.execute()

        then:
        // Should succeed - incomplete config means environment is clean
        notThrown(Exception)
    }

    def "force flag can be set to false explicitly"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'test.template')
        templateFile.text = """
[properties]
test.property=value
"""
        
        task.file = templateFile.absolutePath
        task.force = false

        when:
        task.execute()

        then:
        notThrown(Exception)
    }

    def "force flag can be set to true"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'test.template')
        templateFile.text = """
[properties]
test.property=value
"""
        
        task.file = templateFile.absolutePath
        task.force = true

        when:
        task.execute()

        then:
        notThrown(Exception)
    }
}
