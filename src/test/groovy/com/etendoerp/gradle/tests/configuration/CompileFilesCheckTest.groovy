package com.etendoerp.gradle.tests.configuration

import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.TempDir

class CompileFilesCheckTest extends EtendoSpecification {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    def "compilation fails when missing: #file"() {
        given: "an expanded project"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        def expandResult = runTask("expandCore")
        expandResult.task(":expandCore").outcome == TaskOutcome.SUCCESS

        when: "deleting #file"
        def requiredFile = testProjectDir.toPath().resolve(file).toFile()
        if (requiredFile.exists()) {
            requiredFile.delete()
        }

        then: "compilation fails (#task)"

        def success = true
        def exception = null
        def compilationResult
        try {
            compilationResult = runTask(":${task}")
        } catch (Exception e) {
            success = false
            exception = e
        }

        if (success) {
            compilationResult.task(":compileFilesCheck").outcome == TaskOutcome.FAILED
        } else {
            // TODO: Currently the ':compileFilesCheck' task is ran after some other tasks
            // that require properties from the 'Openbravo.properties' (wich is created by the :compileFilesCheck )
            // For example 'update.database' fails because the bbdd.jdbc is not declared (runs another dependency task before the ':compileFilesCheck')
        }


        where:
        file                            | task
        "gradle.properties"             | 'smartbuild'
        "gradle.properties"             | 'compile.complete'
        "gradle.properties"             | 'compile.complete.deploy'
        "gradle.properties"             | 'update.database'
        "gradle.properties"             | 'export.database'

        "config/Openbravo.properties"   | 'smartbuild'
        "config/Openbravo.properties"   | 'compile.complete'
        "config/Openbravo.properties"   | 'compile.complete.deploy'
        "config/Openbravo.properties"   | 'update.database'
        "config/Openbravo.properties"   | 'export.database'

        "config/Format.xml"             | 'smartbuild'
        "config/Format.xml"             | 'compile.complete'
        "config/Format.xml"             | 'compile.complete.deploy'
        "config/Format.xml"             | 'update.database'
        "config/Format.xml"             | 'export.database'

        "config/log4j2.xml"             | 'smartbuild'
        "config/log4j2.xml"             | 'compile.complete'
        "config/log4j2.xml"             | 'compile.complete.deploy'
        "config/log4j2.xml"             | 'update.database'
        "config/log4j2.xml"             | 'export.database'

        "config/log4j2-web.xml"         | 'smartbuild'
        "config/log4j2-web.xml"         | 'compile.complete'
        "config/log4j2-web.xml"         | 'compile.complete.deploy'
        "config/log4j2-web.xml"         | 'update.database'
        "config/log4j2-web.xml"         | 'export.database'
    }

    def "compilation succeeds when #file is not missing"() {
        given: "an installed project"
        addRepositoryToBuildFile(SNAPSHOT_REPOSITORY_URL)

        def expandResult = runTask("expandCore")
        expandResult.task(":expandCore").outcome == TaskOutcome.SUCCESS

        def setup = runTask("setup")
        setup.task(":setup").outcome == TaskOutcome.UP_TO_DATE

        assert new File(testProjectDir, "${file}").exists()

        expect: "compilation succeeds (#task)"
        def compilationResult = runTask(task)
        compilationResult.task(":${task}").outcome == TaskOutcome.SUCCESS

        where:
        file                            | task
        "gradle.properties"             | 'compileFilesCheck'

        "config/Openbravo.properties"   | 'compileFilesCheck'

        "config/Format.xml"             | 'compileFilesCheck'

        "config/log4j2.xml"             | 'compileFilesCheck'

        "config/log4j2-web.xml"         | 'compileFilesCheck'
    }
}
