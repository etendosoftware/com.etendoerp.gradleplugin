package com.etendoerp.connections

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class DatabaseConnectionExtSpec extends Specification {

    def "registerProjectExt adds createDatabaseConnection closure to project"() {
        given:
        def project = ProjectBuilder.builder().build()

        when:
        DatabaseConnection.registerProjectExt(project)

        then:
        project.hasProperty('createDatabaseConnection')
        project.createDatabaseConnection instanceof Closure

        when: "call the closure without DB properties (should return null when it cannot establish connection)"
        def result = project.createDatabaseConnection(false)

        then:
        result == null
    }
}
