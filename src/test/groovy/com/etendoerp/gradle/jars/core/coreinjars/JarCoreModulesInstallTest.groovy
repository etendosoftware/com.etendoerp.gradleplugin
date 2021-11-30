package com.etendoerp.gradle.jars.core.coreinjars

import com.etendoerp.gradle.jars.EtendoCoreJarSpecificationTest
import com.etendoerp.gradle.jars.core.coreinsources.CoreUtils
import com.etendoerp.gradle.utils.DBCleanupMode
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.TempDir
import spock.lang.Title

@Title("Running the install task with modules dirs and dependencies.")
@Narrative("""When having modules directories in the root dir and modules jar dependencies, running
the 'install' task creates the modules correctly""")
@Stepwise
class JarCoreModulesInstallTest extends EtendoCoreJarSpecificationTest {
    @TempDir @Shared File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    DBCleanupMode cleanDatabase() {
        return DBCleanupMode.ONCE
    }

    public final static String SOURCE_MODULE_GROUP = "com.test"
    public final static String SOURCE_MODULE_NAME  = "moduletoexpand"

    public final static String JAR_MODULE_GROUP = "com.test"
    public final static String JAR_MODULE_NAME  = "dummymodule"

    @Issue("EPL-13")
    def "Running install with modules dir and modules dependencies"() {
        given: "A Etendo environment with the Core Jar dependency"
        def dependenciesTaskResult = runTask(":dependencies","-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS
        //assert dependenciesTaskResult.output.contains(CORE)

        and: "The users adds a sources module dependency"
        def moduleSourceGroup = SOURCE_MODULE_GROUP
        def moduleSourceName = SOURCE_MODULE_NAME
        buildFile << """
        dependencies {
          moduleDeps('${moduleSourceGroup}:${moduleSourceName}:[1.0.0,)@zip') { transitive = true }
        }

        repositories {
          maven {
            url 'https://repo.futit.cloud/repository/etendo-test'
          }
        }
        """

        and: "The users runs the expandCustomModule task passing by command line the module to expand"
        def expandCustomModuleTaskResult = runTask(":expandCustomModule","-Ppkg=${moduleSourceGroup}.${moduleSourceName}","-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        expandCustomModuleTaskResult.task(":expandCustomModule").outcome == TaskOutcome.SUCCESS

        and: "The module will be expanded in the 'modules' dir "
        def moduleLocation = new File("${testProjectDir.getAbsolutePath()}/modules/${moduleSourceGroup}.${moduleSourceName}")
        assert moduleLocation.exists()

        and: "The users adds a jar module dependency"
        def moduleJarGroup = JAR_MODULE_GROUP
        def moduleJarName = JAR_MODULE_NAME

        buildFile << """
        dependencies {
          implementation('${moduleJarGroup}:${moduleJarName}:[1.0.0,)') { transitive = true }
        }
        """

        def dependenciesJarResult = runTask(":dependencies","-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesJarResult.task(":dependencies").outcome == TaskOutcome.SUCCESS
        assert dependenciesJarResult.output.contains("${moduleJarGroup}:${moduleJarName}")

        when: "The users runs the install task"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        then: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "The environment should contain the expanded source module installed"
        assert CoreUtils.containsModule("${moduleSourceGroup}.${moduleSourceName}", getDBConnection())

        and: "The environment should contain the expanded jar module installed"
        assert CoreUtils.containsModule("${moduleJarGroup}.${moduleJarName}", getDBConnection())
    }

}
