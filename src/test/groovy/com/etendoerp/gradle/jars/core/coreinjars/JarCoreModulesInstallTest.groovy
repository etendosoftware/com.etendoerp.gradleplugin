package com.etendoerp.gradle.jars.core.coreinjars

import com.etendoerp.gradle.jars.core.coreinsources.CoreUtils
import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

/**
 * This test should use the latest CORE snapshot
 */

@Title("Running the install task with modules dirs and dependencies.")
@Narrative("""When having modules directories in the root dir and modules jar dependencies, running
the 'install' task creates the modules correctly""")
class JarCoreModulesInstallTest extends EtendoCoreResolutionSpecificationTest {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    public final static String SOURCE_MODULE_GROUP = "com.test"
    public final static String SOURCE_MODULE_NAME  = "moduletoexpand"

    public final static String JAR_MODULE_GROUP = "com.test"
    public final static String JAR_MODULE_NAME  = "dummymodule"

    @Issue("EPL-13")
    def "Running install with modules dir and modules dependencies"() {
        given: "A Etendo environment with the Core dependency"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", ignoreDisplayMenu : true]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The users adds a sources module dependency"
        def moduleSourceGroup = SOURCE_MODULE_GROUP
        def moduleSourceName = SOURCE_MODULE_NAME
        def repoEtendoTest = TEST_REPO
        buildFile << """
        dependencies {
          moduleDeps('${moduleSourceGroup}:${moduleSourceName}:[1.0.0,)@zip') { transitive = true }
        }

        repositories {
          maven {
            url '${repoEtendoTest}'
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

        where:
        coreType  | _
        "sources" | _
        "jar"     | _
    }

}
