package com.etendoerp.gradle.tests.configuration

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("Correct creation of the Openbravo.properties")
@Narrative("""
Test case to verify that running the 'setup' task when the core is in JAR has first task execution,
the Openbravo.properties is correctly created.
""")
@Issue("EPL-313")
class SetupCoreJarTest extends EtendoCoreResolutionSpecificationTest {

    @TempDir
    File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST
    }

    def "Running the setup task when the core is in JAR"() {
        given: "A Etendo core '#coreType'"
        addRepositoryToBuildFile(SNAPSHOT_REPOSITORY_URL)
        addRepositoryToBuildFile(getCoreRepo())

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'"]
        loadCore([coreType: "jar", pluginVariables: pluginVariables])

        and: "The core is not extracted yet"
        File buildLocation = new File(testProjectDir, "build/etendo")
        File buildConfigLocation = new File(buildLocation, "config")

        assert !buildConfigLocation.exists()

        when: "The user runs the setup task"
        def setupResult = runTask("setup")

        then: "The setup will finish successfully"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE

        and: "The Openbravo.properties will be created correctly"
        File configLocation = new File(testProjectDir, "config")
        assert configLocation.exists()

        File openbravoProps = new File(configLocation, "Openbravo.properties")
        assert openbravoProps.exists()

        and: "The Openbravo.properties contains some of the properties defined in the template"

        assert openbravoProps.text.contains("bbdd.rdbms=POSTGRE")
        assert openbravoProps.text.contains("bbdd.driver=org.postgresql.Driver")
        assert openbravoProps.text.contains("bbdd.url=jdbc:postgresql://localhost\\:5432")
    }

}
