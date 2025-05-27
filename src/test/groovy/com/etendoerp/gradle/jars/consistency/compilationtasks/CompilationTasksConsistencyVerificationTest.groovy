package com.etendoerp.gradle.jars.consistency.compilationtasks

import com.etendoerp.consistency.EtendoArtifactsConsistencyContainer
import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

/**
 * This test should use the latest CORE snapshot
 */

@Issue("EPL-123")
@Title("The compilations tasks fails when there is modules not updated in the database (not EQUAL version)")
@Narrative("""
When the users tries to run any compilation task (smartbuild, compile.complete, compile.complete.deploy), if there is
a module not matching the version with the installed one in the database, a error is throw.

The module is inconsistent at the start because is not installed, and later because is not updated in the database.

""")
class CompilationTasksConsistencyVerificationTest extends EtendoCoreResolutionSpecificationTest {
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

    def "Compilation tasks fails when modules are inconsistent"() {
        given: "A user wanting to install a JAR module dependency"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'"]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The user install the Etendo core environment."
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        and: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "The user runs the smartbuild task"
        def smartbuildResult = runTask("smartbuild")
        smartbuildResult.task(":smartbuild").outcome == TaskOutcome.SUCCESS

        and: "The user specifies a JAR module dependency to install"
        String moduleA = "com.test.moduleAextract"

        buildFile << """
            dependencies {
              implementation 'com.test:moduleAextract:1.0.0'
            }
        """

        and: "The user resolves the dependency"
        def dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS

        def locationModuleA = new File(testProjectDir, "build/etendo/modules/${moduleA}")
        assert locationModuleA.exists()

        def artifactPropertiesFile = new File(locationModuleA, "${EtendoArtifactMetadata.METADATA_FILE}")
        assert artifactPropertiesFile.exists()
        assert artifactPropertiesFile.text.contains("1.0.0")

        and: "The users tries to run the smartbuild task, but it should fail because there is inconsistencies"
        runSmartBuildTask(false, EtendoArtifactsConsistencyContainer.JAR_MODULES_CONSISTENT_ERROR)

        when: "The users runs the update.database task"
        def updateResult = runTask(":update.database")

        and: "The environment will be updated correctly"
        updateResult.task(":update.database").outcome == TaskOutcome.SUCCESS

        then: "The user should be able to run the smartbuild task without errors"
        runSmartBuildTask(true)

        // UPDATE MODULE

        when: "The users wants to update the installed module"
        buildFile << """
            dependencies {
              implementation 'com.test:moduleAextract:1.0.1'
            }
        """

        dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS

        locationModuleA = new File(testProjectDir, "build/etendo/modules/${moduleA}")
        assert locationModuleA.exists()

        artifactPropertiesFile = new File(locationModuleA, "${EtendoArtifactMetadata.METADATA_FILE}")
        assert artifactPropertiesFile.exists()
        assert artifactPropertiesFile.text.contains("1.0.1")

        then: "A error should be throw running the smartbuild task"
        runSmartBuildTask(false, EtendoArtifactsConsistencyContainer.JAR_MODULES_CONSISTENT_ERROR)

        when: "The user updates the database"
        updateResult = runTask(":update.database")

        and: "The environment will be updated correctly"
        updateResult.task(":update.database").outcome == TaskOutcome.SUCCESS

        then: "The smartbuild task should not fail"
        runSmartBuildTask(true)

        where:
        coreType  | _
        "sources" | _
        "jar"     | _
    }

}
