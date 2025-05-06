package com.etendoerp.gradle.jars.configuration

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.TempDir
import spock.lang.Title

/**
 * This test should use the latest CORE snapshot
 */

@Title("Series of tests to verify that the task 'prepareConfig' creates the 'Openbravo.properties' file correctly.")
class PrepareConfigJarTest extends EtendoCoreResolutionSpecificationTest {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST_SNAPSHOT
    }

    def "Default values when 'gradle properties' is empty or does not exists"() {
        given: "A Project with the Etendo core jar"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", forceResolution : true]
        loadCore([coreType : "jar", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "jar", testProjectDir: testProjectDir])

        and: "The gradle.properties file is empty"
        def gradleProperties = new File("${getProjectDir().absolutePath}/gradle.properties")
        if (gradleProperties.exists()) {
            gradleProperties.text = ""
        }

        when: "The 'prepareConfig' task is ran"
        def result = runTask("prepareConfig")

        then: "the Openbravo.properties file is created with the correct data"
        def propsFile = new File(testProjectDir, "config/Openbravo.properties")
        def props = new Properties()

        props.load(propsFile.newReader())
        verifyAll {
            result.task(":prepareConfig").outcome == TaskOutcome.UP_TO_DATE || TaskOutcome.SUCCESS

            props.getProperty("context.name") == "etendo"
            props.getProperty("bbdd.sid") == "etendo"
            props.getProperty("bbdd.systemUser") == "postgres"
            props.getProperty("bbdd.systemPassword") == "syspass"
            props.getProperty("bbdd.user") == "tad"
            props.getProperty("bbdd.password") == "tad"
            props.getProperty("source.path") == testProjectDir.absolutePath
            props.getProperty("attach.path") == "${testProjectDir.absolutePath}/attachments"
            props.getProperty("bbdd.url") == "jdbc:postgresql://localhost:5433"
            props.getProperty("allow.root") == "false"
        }
    }

    def "Create Openbravo.properties using the 'gradle properties' file"() {
        given: "A Project with the Etendo core jar"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", forceResolution : true]
        loadCore([coreType : "jar", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "jar", testProjectDir: testProjectDir])

        and: "A configured gradle.properties"
        def gradleProperties = new File(testProjectDir, "gradle.properties")
        gradleProperties.text = """
        source.path=${sourcepath}
        context.name=${contextname}
        bbdd.host=${host}
        bbdd.port=${port}
        bbdd.sid=${bbddsid}
        bbdd.systemUser=${bbddsystemUser}
        bbdd.systemPassword=${bbddsystemPassword}
        bbdd.user=${bbdduser}
        bbdd.password=${bbddpassword}
        attach.path=${attachpath}
        allow.root=${allowroot}
        """

        when: "The 'prepareConfig' task is ran"
        def result = runTask("prepareConfig")

        then: "the Openbravo.properties file is created with the correct data"
        def propsFile = new File(testProjectDir, "config/Openbravo.properties")
        def props = new Properties()

        props.load(propsFile.newReader())
        verifyAll {
            result.task(":prepareConfig").outcome == TaskOutcome.UP_TO_DATE || TaskOutcome.SUCCESS

            props.getProperty("context.name")        == contextname
            props.getProperty("bbdd.sid")            == bbddsid
            props.getProperty("bbdd.systemUser")     == bbddsystemUser
            props.getProperty("bbdd.systemPassword") == bbddsystemPassword
            props.getProperty("bbdd.user")           == bbdduser
            props.getProperty("bbdd.password")       == bbddpassword
            props.getProperty("source.path")         == sourcepath
            props.getProperty("attach.path")         == attachpath
            props.getProperty("bbdd.url")            == "jdbc:postgresql://" + host + ":" + port
            props.getProperty("allow.root")          == allowroot
        }

        where:
        contextname  | bbddsid        | bbddsystemUser       | bbddsystemPassword   | bbdduser        | bbddpassword    | sourcepath        | attachpath                   | host    | port   | allowroot
        "etendo"     | "test_db_jar"  | "test_postgres_jar"  | "test_postgres_jar"  | "test_tad_jar"  | "test_tad_jar"  | "/test/jar/path/" | "/test/jar/path/attachments" | "test"  | "5439" | "false"
        "etendo2"    | "test_db_jar2" | "test_postgres_jar2" | "test_postgres_jar2" | "test_tad_jar2" | "test_tad_jar2" | "/test/jar/path/" | "/test/jar/path/attachments" | "test2" | "5002" | "true"
    }

    def "Running 'prepareConfig' task multiple times"() {
        given: "A Project with the Etendo core jar"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", forceResolution : true]
        loadCore([coreType : "jar", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "jar", testProjectDir: testProjectDir])

        and: "a configured gradle.properties"
        def gradleProperties = new File(testProjectDir, "gradle.properties")
        gradleProperties.text = """
        source.path=/test/source/path/
        context.name=test_etendo
        bbdd.port=5439
        bbdd.sid=test_db
        bbdd.systemUser=test_postgres
        bbdd.systemPassword=test_postgres
        bbdd.user=test_tad
        bbdd.password=test_tad
        attach.path=/test/source/path/attachments
        """

        and: "running the first 'prepareConfig' task"
        def prepareConfigResult = runTask("prepareConfig")
        prepareConfigResult.task(":prepareConfig").outcome == TaskOutcome.UP_TO_DATE || TaskOutcome.SUCCESS

        when: "running the 'prepareConfig' task again, after changing the gradle.properties contents"
        gradleProperties << """
        context.name=test_etendo2
        bbdd.host=testhost
        """
        def result = runTask("prepareConfig")

        then: "the Openbravo.properties file is created with the correct data"
        def propsFile = new File(testProjectDir, "config/Openbravo.properties")
        def props = new Properties()
        props.load(propsFile.newReader())

        verifyAll {
            result.task(":prepareConfig").outcome == TaskOutcome.UP_TO_DATE || TaskOutcome.SUCCESS

            props.getProperty("context.name") == "test_etendo2"
            props.getProperty("bbdd.sid") == "test_db"
            props.getProperty("bbdd.systemUser") == "test_postgres"
            props.getProperty("bbdd.systemPassword") == "test_postgres"
            props.getProperty("bbdd.user") == "test_tad"
            props.getProperty("bbdd.password") == "test_tad"
            props.getProperty("source.path") == "/test/source/path/"
            props.getProperty("attach.path") == "/test/source/path/attachments"
            props.getProperty("bbdd.url") == "jdbc:postgresql://testhost:5439"
        }
    }

}
