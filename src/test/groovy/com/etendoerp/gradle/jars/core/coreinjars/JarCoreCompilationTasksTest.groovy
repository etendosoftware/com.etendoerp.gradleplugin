package com.etendoerp.gradle.jars.core.coreinjars

import com.etendoerp.gradle.jars.EtendoCoreJarSpecificationTest
import com.etendoerp.gradle.utils.DBCleanupMode
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Stepwise
import spock.lang.TempDir
import spock.lang.Title

@Title("Running compilation tasks using a Etendo core JAR")
@Stepwise
class JarCoreCompilationTasksTest extends EtendoCoreJarSpecificationTest{
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
    String getCoreVersion() {
        return ETENDO_22q1_VERSION
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    @Issue("EPL-13")
    def "Running compilation tasks" () {
        given: "A Etendo environment with the Core Jar dependency"
        def dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies","-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS
        assert dependenciesTaskResult.output.contains(getCore())

        when: "The users runs the install task"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        then: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS
    }

    @Issue("EPL-13")
    def "successfully runs #task after install"() {
        expect: "successfully runs #task"
        def result = runTask(task)
        result.task(":${task}").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE

        where:
        task                        | _
        "smartbuild"                | _
        "compile.complete"          | _
        "compile.complete.deploy"   | _
        "update.database"           | _
        "export.database"           | _
    }
}
