package com.etendoerp.gradle.jars.core.coreinsources

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.gradle.utils.DBCleanupMode
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.TempDir
import spock.lang.Title

/**
 * This test should use the latest CORE snapshot
 */

@Title("Test to verify that a Etendo module Jar dependency is installed correctly")
@Narrative("""This test adds a Etendo module jar dependency 
and runs the Ant install tasks to verify that the module is correctly installed""")
@Stepwise
class SourceCoreJarModuleInstallTest extends EtendoCoreResolutionSpecificationTest {

    @TempDir @Shared File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    DBCleanupMode cleanDatabase() {
        return DBCleanupMode.ONCE
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST_SNAPSHOT
    }

    public final static String JAR_MODULE_GROUP = "com.test"
    public final static String JAR_MODULE_NAME  = "dummymodule"

    def "Installing Etendo sources core with a module in Jar"() {
        given: "A Etendo sources core environment"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", ignoreDisplayMenu : true]
        loadCore([coreType : "sources", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "sources", testProjectDir: testProjectDir])

        and: "The users adds a jar module dependency"
        def moduleGroup = JAR_MODULE_GROUP
        def moduleName = JAR_MODULE_NAME
        def repoEtendoTest = TEST_REPO
        buildFile << """
        dependencies {
          implementation('${moduleGroup}:${moduleName}:[1.0.0,)') { transitive = true }
        }
        
        repositories {
          maven {
            url '${repoEtendoTest}'
          }
        }
        
        etendo {
            ignoreConsistencyVerification = true 
        }
        
        """

        and: "The users runs the 'dependencies' task"
        def dependenciesResult = runTask("dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        assert dependenciesResult.task(":dependencies").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE

        and: "The dependencies contains the Jar module"
        assert dependenciesResult.output.contains("${moduleGroup}:${moduleName}")

        when: "The users runs the install task"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        then: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "The environment should contain the jar module"
        assert CoreUtils.containsModule("${moduleGroup}.${moduleName}", getDBConnection())
    }

    def "successfully runs #task after install a Jar module"() {
        expect: "successfully runs #task"
        def result = runTask(task)
        result.task(":${task}").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE

        where:
        task                        | _
        "update.database"           | _
        "export.database"           | _
        "smartbuild"                | _
        "compile.complete"          | _
        "compile.complete.deploy"   | _
    }
}
