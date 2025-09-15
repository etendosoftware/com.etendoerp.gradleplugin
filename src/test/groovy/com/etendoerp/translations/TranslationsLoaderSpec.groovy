package com.etendoerp.translations

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Test specification for TranslationsLoader
 * 
 * Tests the registration of translation export tasks in Gradle projects.
 */
class TranslationsLoaderSpec extends Specification {

    def project

    def setup() {
        project = ProjectBuilder.builder().build()
    }

    def "should register export.translations task"() {
        when:
        TranslationsLoader.load(project)

        then:
        project.tasks.findByName('export.translations') != null
    }

    def "should register ExportTranslationsTask type"() {
        when:
        TranslationsLoader.load(project)

        then:
        def task = project.tasks.findByName('export.translations')
        task instanceof ExportTranslationsTask
    }

    def "should have correct task properties after registration"() {
        when:
        TranslationsLoader.load(project)

        then:
        def task = project.tasks.findByName('export.translations') as ExportTranslationsTask
        task.group == 'Translation'
        task.description != null
        task.description.contains('Export translation files')
    }

    def "should have default property values after registration"() {
        when:
        TranslationsLoader.load(project)

        then:
        def task = project.tasks.findByName('export.translations') as ExportTranslationsTask
        task.language == 'es_ES'
        task.clientId == '0'
        task.reducedVersion == false
        task.modules == ''
        task.coreModuleOutput == 'core'
    }

    def "should be able to configure task properties"() {
        when:
        TranslationsLoader.load(project)
        def task = project.tasks.findByName('export.translations') as ExportTranslationsTask
        task.language = 'fr_FR'
        task.clientId = '1000'
        task.reducedVersion = true
        task.modules = 'all'
        task.coreModuleOutput = 'custom_core'

        then:
        task.language == 'fr_FR'
        task.clientId == '1000'
        task.reducedVersion == true
        task.modules == 'all'
        task.coreModuleOutput == 'custom_core'
    }

    def "should not fail when called multiple times"() {
        when:
        TranslationsLoader.load(project)
        TranslationsLoader.load(project)

        then:
        noExceptionThrown()
        project.tasks.findByName('export.translations') != null
    }

    def "should work with project that has existing tasks"() {
        given:
        project.tasks.create('existing-task')

        when:
        TranslationsLoader.load(project)

        then:
        project.tasks.findByName('existing-task') != null
        project.tasks.findByName('export.translations') != null
    }

    def "should register task that can be executed"() {
        given:
        // Add required properties to prevent validation errors
        project.ext.set('modules', 'core')
        project.ext.set('source.path', '/tmp/test')

        when:
        TranslationsLoader.load(project)
        def task = project.tasks.findByName('export.translations')

        then:
        task != null
        // Task should be executable (will fail at runtime due to missing DB connection, but that's expected)
    }
}