package com.etendoerp.gradle.jars.core.coreinsources

import com.etendoerp.gradle.tests.EtendoSpecification
import com.etendoerp.gradle.utils.DBCleanupMode
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.TempDir
import spock.lang.Title

@Title("Test to verify that a Etendo module Jar dependency is installed correctly")
@Narrative("""This test adds a Etendo module jar dependency 
and runs the Ant install tasks to verify that the module is correctly installed""")
class SourceCoreJarModuleInstallTest extends EtendoSpecification {

    @TempDir @Shared File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    DBCleanupMode cleanDatabase() {
        return DBCleanupMode.ONCE
    }

    public final static String JAR_MODULE_GROUP = "com.test"
    public final static String JAR_MODULE_NAME  = "dummymodule"

    def "Installing Etendo sources core with a module in Jar"() {
        given: "A Etendo sources core environment"
        def expandResult = runTask(":expand")
        assert expandResult.task(":expand").outcome == TaskOutcome.SUCCESS

        and: "The users adds a jar module dependency"
        def moduleGroup = JAR_MODULE_GROUP
        def moduleName = JAR_MODULE_NAME
        buildFile << """
        dependencies {
          moduleDeps('${moduleGroup}:${moduleName}:[1.0.0,)') { transitive = true }
        }
        
        repositories {
          maven {
            url 'https://repo.futit.cloud/repository/etendo-test'
          }
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

        and: "The environment should contain the source module"
        assert CoreUtils.containsModule("${moduleGroup}.${moduleName}", getDBConnection())
    }
}
