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

    def "compilation fails when missing: #file"() {
        given: "an expanded project"
        def expandResult = runTask("expand")
        expandResult.task(":expand").outcome == TaskOutcome.SUCCESS

        when: "deleting #file"
        def requiredFile = testProjectDir.toPath().resolve(file).toFile()
        if (requiredFile.exists()) {
            requiredFile.delete()
        }

        then: "compilation fails (#task)"
        def compilationResult = runTaskAndFail("smartbuild")
        compilationResult.task(":compileFilesCheck").outcome == TaskOutcome.FAILED

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

    def "compilation succeeds (#task) when #file is not missing"() {
        given: "an installed project"
        def expandResult = runTask("expand")
        expandResult.task(":expand").outcome == TaskOutcome.SUCCESS
        def setup = runTask("setup")
        setup.task(":setup").outcome == TaskOutcome.UP_TO_DATE
        def install = runTask("install")
        install.task(":install").outcome == TaskOutcome.UP_TO_DATE || install.task(":install").outcome == TaskOutcome.SUCCESS

        expect: "compilation succeeds (#task)"
        def compilationResult = runTask("smartbuild")
        compilationResult.task(":compileFilesCheck").outcome == TaskOutcome.SUCCESS

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
}