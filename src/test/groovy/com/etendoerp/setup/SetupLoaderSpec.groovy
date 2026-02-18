package com.etendoerp.setup

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Tests for SetupLoader
 */
class SetupLoaderSpec extends Specification {

    @TempDir
    Path tempDir

    def project

    def setup() {
        project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()
    }

    def "load registers setup.applyTemplates task"() {
        when:
        SetupLoader.load(project)

        then:
        project.tasks.findByName('setup.applyTemplates') != null
    }

    def "load registers task with correct type"() {
        when:
        SetupLoader.load(project)
        def task = project.tasks.findByName('setup.applyTemplates')

        then:
        task != null
        task instanceof SetupApplyTemplatesTask
    }

    def "load throws exception when called multiple times"() {
        given:
        SetupLoader.load(project)

        when:
        SetupLoader.load(project)

        then:
        thrown(Exception)
    }

    def "registered task has correct group"() {
        when:
        SetupLoader.load(project)
        def task = project.tasks.findByName('setup.applyTemplates')

        then:
        task.group == 'setup'
    }

    def "registered task has correct description"() {
        when:
        SetupLoader.load(project)
        def task = project.tasks.findByName('setup.applyTemplates')

        then:
        task.description == 'Apply configuration templates to the project'
    }

    def "registered task has all expected properties"() {
        when:
        SetupLoader.load(project)
        def task = project.tasks.findByName('setup.applyTemplates') as SetupApplyTemplatesTask

        then:
        task.template == null
        task.file == null
        task.url == null
        task.force == false
    }
}
