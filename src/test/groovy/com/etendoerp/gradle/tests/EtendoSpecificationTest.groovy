package com.etendoerp.gradle.tests

import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.TempDir
import spock.lang.Title

@Title("Tests for the Etendo Spock Specification")
@Narrative("""
EtendoSpecification is the class all tests should extend, so it should configure the project properly
""")
class EtendoSpecificationTest extends EtendoSpecification {
    @TempDir @Shared File testProjectDir

    @Issue("ERP-431")
    def "base test class configures the project properly"() {
        when:
        def result = runTask("init")

        then:
        // Skipped because build.gradle already exists
        // No prior errors to executing the task
        result.task(":init").outcome == TaskOutcome.SKIPPED
        // Nexus user (and therefore password) should be read from gradle.properties
        args.containsKey("nexusUser") && args.get("nexusUser") != null
    }

    def "JAVA_HOME is declared properly"() {
        expect:
        System.getenv("JAVA_HOME") != null
        System.getenv("JAVA_HOME") != "null"
    }

    @Override
    File getProjectDir() {
        return testProjectDir
    }
}
