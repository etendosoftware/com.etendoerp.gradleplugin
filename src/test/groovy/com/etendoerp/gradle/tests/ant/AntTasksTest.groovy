package com.etendoerp.gradle.tests.ant

import com.etendoerp.gradle.tests.EtendoSpecification
import com.etendoerp.gradle.utils.DBCleanupMode
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.TempDir
import spock.lang.Title

@Title("Test for common ant tasks executed via gradle")
@Stepwise
class AntTasksTest extends EtendoSpecification {
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


    def isInstalled(TaskOutcome expandOutcome, TaskOutcome setupOutcome ,TaskOutcome installOutcome) {
        assert expandOutcome == TaskOutcome.SUCCESS
        assert setupOutcome == TaskOutcome.UP_TO_DATE
        assert (installOutcome == TaskOutcome.UP_TO_DATE) || (installOutcome == TaskOutcome.SUCCESS)

        installed = true
    }

    def "successfully installs"() {
        when: "expanding, configuring and installing the environment"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        def expandResult = runTask("expandCore")
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        then: "all tasks run successfully"
        isInstalled(expandResult.task(":expandCore").outcome, setupResult.task(":setup").outcome, installResult.task(":install").outcome)
    }

    def "successfully runs #task"() {
        given: "an installed project"
        installed

        when: "running smartbuild"
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
