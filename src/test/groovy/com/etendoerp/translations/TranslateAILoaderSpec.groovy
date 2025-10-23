package com.etendoerp.translations

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Test specification for TranslateAILoader
 * 
 * Tests the registration of AI translation tasks in Gradle projects.
 */
class TranslateAILoaderSpec extends Specification {

    def project

    def setup() {
        project = ProjectBuilder.builder().build()
    }

    def "should register translate.ai task"() {
        when:
        TranslateAILoader.load(project)

        then:
        project.tasks.findByName('translate.ai') != null
    }

    def "should register TranslateAITask type"() {
        when:
        TranslateAILoader.load(project)

        then:
        def task = project.tasks.findByName('translate.ai')
        task instanceof TranslateAITask
    }

    def "should have correct task properties after registration"() {
        when:
        TranslateAILoader.load(project)

        then:
        def task = project.tasks.findByName('translate.ai') as TranslateAITask
        task.group == 'Translation'
        task.description != null
        task.description.contains('Auto-translate using OpenAI')
    }

    def "should have default property values after registration"() {
        when:
        TranslateAILoader.load(project)

        then:
        def task = project.tasks.findByName('translate.ai') as TranslateAITask
        task.language == 'es_ES'
        task.clientId == '0'
        task.modules == ''
        task.coreModuleOutput == 'core'
        task.openaiModel == 'gpt-5-nano'
    }

    def "should be able to configure task properties"() {
        when:
        TranslateAILoader.load(project)
        def task = project.tasks.findByName('translate.ai') as TranslateAITask
        task.language = 'fr_FR'
        task.clientId = '1000'
        task.modules = 'all'
        task.coreModuleOutput = 'custom_core'
        task.openaiModel = 'gpt-4'

        then:
        task.language == 'fr_FR'
        task.clientId == '1000'
        task.modules == 'all'
        task.coreModuleOutput == 'custom_core'
        task.openaiModel == 'gpt-4'
    }

    def "should not fail when called multiple times"() {
        when:
        TranslateAILoader.load(project)
        TranslateAILoader.load(project)

        then:
        noExceptionThrown()
        project.tasks.findByName('translate.ai') != null
    }

    def "should work with project that has existing tasks"() {
        given:
        project.tasks.create('existing-task')

        when:
        TranslateAILoader.load(project)

        then:
        project.tasks.findByName('existing-task') != null
        project.tasks.findByName('translate.ai') != null
    }

    def "should register task that can be executed"() {
        given:
        // Add required properties to prevent validation errors
        project.ext.set('modules', 'core')
        project.ext.set('source.path', '/tmp/test')
        project.ext.set('OPENAI_API_KEY', 'sk-test-key')

        when:
        TranslateAILoader.load(project)
        def task = project.tasks.findByName('translate.ai')

        then:
        task != null
        // Task should be executable (will fail at runtime due to missing DB connection, but that's expected)
    }

    def "should work alongside regular translation loader"() {
        when:
        TranslationsLoader.load(project)
        TranslateAILoader.load(project)

        then:
        project.tasks.findByName('export.translations') != null
        project.tasks.findByName('translate.ai') != null
        project.tasks.findByName('export.translations') instanceof ExportTranslationsTask
        project.tasks.findByName('translate.ai') instanceof TranslateAITask
    }

    def "should have different task names for both loaders"() {
        when:
        TranslationsLoader.load(project)
        TranslateAILoader.load(project)

        then:
        def exportTask = project.tasks.findByName('export.translations')
        def aiTask = project.tasks.findByName('translate.ai')
        exportTask != null
        aiTask != null
        exportTask != aiTask
    }

    def "should have both tasks in Translation group"() {
        when:
        TranslationsLoader.load(project)
        TranslateAILoader.load(project)

        then:
        def exportTask = project.tasks.findByName('export.translations') as ExportTranslationsTask
        def aiTask = project.tasks.findByName('translate.ai') as TranslateAITask
        exportTask.group == 'Translation'
        aiTask.group == 'Translation'
    }
}