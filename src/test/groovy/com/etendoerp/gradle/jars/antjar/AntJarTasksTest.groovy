package com.etendoerp.gradle.jars.antjar

import com.etendoerp.gradle.jars.EtendoCoreJarSpecificationTest
import com.etendoerp.gradle.utils.DBCleanupMode
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.TempDir
import spock.lang.Title

@Title("Test for the ant tasks using the Etendo core jar")
@Narrative("""TODO: This test is currently failing because the 'build.xml' file is taken from the root project.
The plugin should choose where find the 'build.xml', in the root project (core sources) or in the 'build/etendo' dir (core jar)
""")
@Stepwise
class AntJarTasksTest extends EtendoCoreJarSpecificationTest{

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

    def "successfully installs"() {
        given: "A Project with the Etendo core jar"
        def dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies","-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS
        assert dependenciesTaskResult.output.contains(CORE)

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
