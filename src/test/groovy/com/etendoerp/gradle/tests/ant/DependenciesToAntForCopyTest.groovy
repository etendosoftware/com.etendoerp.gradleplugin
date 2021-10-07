package com.etendoerp.gradle.tests.ant

import com.etendoerp.gradle.tests.EtendoSpecification
import spock.lang.TempDir

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS


class DependenciesToAntForCopyTest extends EtendoSpecification {
    @TempDir File testProjectDir

    def "gradle.libs is defined in ant"() {
        given: "an expanded project"
        def expandResult = runTask("expand")
        expandResult.task(":expand").outcome == SUCCESS

        when: "running the antInit task"
        def antInitResult =  runTask("antInit")

        then: "init succeedes due to gradle libraries being defined in ant's context"
        antInitResult.task(":antInit").outcome == SUCCESS
    }

    def "running ant directly fails"() {
        given: "an expanded project"
        def expandResult = runTask("expand")
        expandResult.task(":expand").outcome == SUCCESS

        when: "running ant directly"
        def antProcess = "ant init".execute()
        def exitCode = antProcess.waitFor()

        then: "task fails"
        exitCode == 1 // 1 means failure
    }

    @Override
    File getProjectDir() {
        return testProjectDir
    }
}
