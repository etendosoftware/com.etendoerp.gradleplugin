package com.etendoerp.gradle.tests.ant

import com.etendoerp.gradle.tests.EtendoSpecification
import spock.lang.TempDir

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS


class DependenciesToAntForCopyTest extends EtendoSpecification {
    @TempDir File testProjectDir

    def "gradle.libs is defined in ant"() {
        given: "an expanded project"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        def expandResult = runTask("expandCore")
        expandResult.task(":expandCore").outcome == SUCCESS

        when: "running the antInit task"
        def antInitResult =  runTask("antInit")

        then: "init succeeds due to gradle libraries being defined in ant's context"
        antInitResult.task(":antInit").outcome == SUCCESS
    }

    def "running ant directly fails"() {
        given: "an expanded project"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)
        def expandResult = runTask("expandCore")
        expandResult.task(":expandCore").outcome == SUCCESS

        when: "running ant directly"
        def success = true
        def exception = null
        def exitCore
        try {
            def antProcess = "ant init".execute()
            exitCode = antProcess.waitFor()
        } catch (Exception e) {
            success = false
            exception = e
        }

        then: "task fails"
        if (success) {
            assert exitCode == 1 || exitCode == 2 // 1 and 2 means failure
        } else {
            assert exception
            assert exception.message.contains("error=2")
        }

    }

    @Override
    File getProjectDir() {
        return testProjectDir
    }
}
