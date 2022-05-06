package com.etendoerp.gradle.tests.configuration

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("setup task does not copy the user credentials")
@Narrative("""
When the user runs the setup task, the credentials in the gradle.properties file
should not be copy to the Openbravo.properties file.
""")
@Issue("EPL-118")
class SetupOmitCredentialsTest extends EtendoCoreResolutionSpecificationTest {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreRepo() {
        return SNAPSHOT_REPOSITORY_URL
    }

    @Override
    String getCoreVersion() {
        return ETENDO_21q1_SNAPSHOT
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    def "The setup task does not copy the nexus credentials"() {
        given: "A user installing the Etendo environment"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", forceResolution : true]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The users configure the nexus credentials"
        File gradlePropertiesFile = new File(testProjectDir, "gradle.properties")

        gradlePropertiesFile << """
        nexusUser=${args.get("nexusUser")}}
        nexusPassword=${args.get("nexusPassword")}}
        """

        when: "The users runs the setup task"
        def setupResult = runTask("setup")

        then: "The task will finish successfully"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE

        and: "The Openbravo.properties will be created without the user credentials"
        File openbravoPropertiesFile = new File(testProjectDir, "config/Openbravo.properties")
        assert openbravoPropertiesFile.exists()

        String propertiesContent = openbravoPropertiesFile.text
        assert !propertiesContent.contains("nexusUser")
        assert !propertiesContent.contains("nexusPassword")

        where:
        coreType  | _
        "sources" | _
        "jar"     | _
    }

}
