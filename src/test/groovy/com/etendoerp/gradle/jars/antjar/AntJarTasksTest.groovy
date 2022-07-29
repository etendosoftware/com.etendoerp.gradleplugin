package com.etendoerp.gradle.jars.antjar


import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.gradle.utils.DBCleanupMode
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.TempDir
import spock.lang.Title

/**
 * This test should use the latest CORE snapshot
 */

@Title("Test for the ant tasks using the Etendo core jar")
@Stepwise
class AntJarTasksTest extends EtendoCoreResolutionSpecificationTest {

    // TODO: This test should resolve from EtendoCoreResolutionSpecificationTest
    // TODO: Use latest snapshot

    @TempDir @Shared File testProjectDir
    boolean installed = false

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    @Override
    DBCleanupMode cleanDatabase() {
        return DBCleanupMode.ONCE
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    def isInstalled(TaskOutcome setupOutcome, TaskOutcome installOutcome) {
        assert setupOutcome == TaskOutcome.UP_TO_DATE
        assert (installOutcome == TaskOutcome.UP_TO_DATE) || (installOutcome == TaskOutcome.SUCCESS)

        installed = true
    }

    String getCoreVersion() {
        return ETENDO_LATEST_SNAPSHOT
    }

    String getCoreRepo() {
        return SNAPSHOT_REPOSITORY_URL
    }

    def "successfully installs"() {
        given: "A Project with the Etendo core jar"
        addRepositoryToBuildFile(getCoreRepo())

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'"]
        loadCore([coreType : "jar", pluginVariables: pluginVariables])
        
        and: "The user resolves the core"
        resolveCore([coreType : "jar", testProjectDir: testProjectDir])

        when: "configuring and installing the environment"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        then: "all tasks run successfully"
        isInstalled(setupResult.task(":setup").outcome, installResult.task(":install").outcome)
    }

    def "successfully runs #task"() {
        given: "an installed project"
        installed

        when: "running the ant task #task"
        def result = runTask(task)

        then: "task completes successfully"
        result.task(":${task}").outcome == TaskOutcome.SUCCESS

        where:
        task                        | _
        "smartbuild"                | _
        "compile.complete"          | _
        "compile.complete.deploy"   | _
        "update.database"           | _
        "export.database"           | _
    }
}
