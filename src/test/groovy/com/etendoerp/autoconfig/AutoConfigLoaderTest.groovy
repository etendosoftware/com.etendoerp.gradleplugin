package com.etendoerp.autoconfig

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class AutoConfigLoaderTest extends Specification {

    def "load should register setup.autoConfig task"() {
        given:
        Project project = ProjectBuilder.builder().build()

        when:
        AutoConfigLoader.load(project)

        then:
        def task = project.tasks.findByName("setup.autoConfig")
        task != null
        task instanceof AutoConfigTask
        task.group == "Etendo Auto-Config"
        task.description == "Runs auto-configuration tasks for registered modules (requires Tomcat)."
    }
}
