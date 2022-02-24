package com.etendoerp.gradle.tests.configuration

import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

/**
 * The 'source.path' property is changed by the Setup task, using the 'System.getProperty("user.dir")'.
 * file: ConfigurationApp.java - line: 833
 */

@Title("Setup Task Tests")
@Narrative("""
Collection of tests for the Setup task.
The setup task should configure the Openbravo.properties:
 - When the file is not present, copy Openbravo.properties.template and replace property values with those defined in gradle.properties
 - When the file is present, replace property values with those defined in gradle.properties
 - When the gradle.properties is empty, some default values are used. 
""")
class SetupTest extends EtendoSpecification {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    def "Create Openbravo.properties on first setup"() {
        given: "a configured gradle.properties"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        def gradleProperties = new File(testProjectDir, "gradle.properties")
        gradleProperties.text = """
        source.path=/test/source/path
        context.name=test_etendo
        bbdd.port=5439
        bbdd.sid=test_db
        bbdd.systemUser=test_postgres
        bbdd.systemPassword=test_postgres
        bbdd.user=test_tad
        bbdd.password=test_tad
        attach.path=/test/source/path/attachments
        """

        when: "running the setup"
        def expandResult = runTask("expandCore")
        def result = runTask("setup")

        then: "the Openbravo.properties file is created with the correct data"
        def propsFile = new File(testProjectDir, "config/Openbravo.properties")
        def props = new Properties()
        props.load(propsFile.newReader())
        verifyAll {
            expandResult.task(":expandCore").outcome == TaskOutcome.SUCCESS
            result.task(":setup").outcome == TaskOutcome.UP_TO_DATE

            props.getProperty("context.name") == "test_etendo"
            props.getProperty("bbdd.sid") == "test_db"
            props.getProperty("bbdd.systemUser") == "test_postgres"
            props.getProperty("bbdd.systemPassword") == "test_postgres"
            props.getProperty("bbdd.user") == "test_tad"
            props.getProperty("bbdd.password") == "test_tad"
            props.getProperty("source.path") == "${getTestProjectDir().absolutePath}"
            props.getProperty("attach.path") == "/test/source/path/attachments"
            props.getProperty("bbdd.url") == "jdbc:postgresql://localhost:5439"
        }
    }


    def "Edit Openbravo.properties when it already exists"() {
        given: "a configured gradle.properties"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        def gradleProperties = new File(testProjectDir, "gradle.properties")
        gradleProperties.text = """
        source.path=/test/source/path
        context.name=test_etendo
        bbdd.port=5439
        bbdd.sid=test_db
        bbdd.systemUser=test_postgres
        bbdd.systemPassword=test_postgres
        bbdd.user=test_tad
        bbdd.password=test_tad
        attach.path=/test/source/path/attachments
        """

        and: "running the first setup"
        def expandResult = runTask("expandCore")
        def firstSetupResult = runTask("setup")
        expandResult.task(":expandCore").outcome == TaskOutcome.SUCCESS
        firstSetupResult.task(":setup").outcome == TaskOutcome.UP_TO_DATE

        when: "running the setup again, after chaning the gradle.properties contents"
        gradleProperties << """
        context.name=test_etendo2
        bbdd.host=testhost
        """
        def result = runTask("setup")

        then: "the Openbravo.properties file is created with the correct data"
        def propsFile = new File(testProjectDir, "config/Openbravo.properties")
        def props = new Properties()
        props.load(propsFile.newReader())
        verifyAll {
            result.task(":setup").outcome == TaskOutcome.UP_TO_DATE

            props.getProperty("context.name") == "test_etendo2"
            props.getProperty("bbdd.sid") == "test_db"
            props.getProperty("bbdd.systemUser") == "test_postgres"
            props.getProperty("bbdd.systemPassword") == "test_postgres"
            props.getProperty("bbdd.user") == "test_tad"
            props.getProperty("bbdd.password") == "test_tad"
            props.getProperty("source.path") == "${getTestProjectDir().absolutePath}"
            props.getProperty("attach.path") == "/test/source/path/attachments"
            props.getProperty("bbdd.url") == "jdbc:postgresql://testhost:5439"
        }
    }

    def "Use default values when gradle.properties is empty or does not exists"() {
        given: "no gradle.properties"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        def gradleProperties = new File(testProjectDir, "gradle.properties")
        gradleProperties.text = ""

        when: "running the setup"
        def expandResult = runTask("expandCore")
        def result = runTask("setup")

        then: "the Openbravo.properties file is created with the correct data"
        def propsFile = new File(testProjectDir, "config/Openbravo.properties")
        def props = new Properties()
        props.load(propsFile.newReader())
        verifyAll {
            expandResult.task(":expandCore").outcome == TaskOutcome.SUCCESS
            result.task(":setup").outcome == TaskOutcome.UP_TO_DATE

            props.getProperty("context.name") == "etendo"
            props.getProperty("bbdd.sid") == "etendo"
            props.getProperty("bbdd.systemUser") == "postgres"
            props.getProperty("bbdd.systemPassword") == "syspass"
            props.getProperty("bbdd.user") == "tad"
            props.getProperty("bbdd.password") == "tad"
            props.getProperty("source.path") == testProjectDir.absolutePath
            props.getProperty("attach.path") == "${testProjectDir.absolutePath}/attachments"
            props.getProperty("bbdd.url") == "jdbc:postgresql://localhost:5432"
            props.getProperty("allow.root") == "false"
        }
    }

}
