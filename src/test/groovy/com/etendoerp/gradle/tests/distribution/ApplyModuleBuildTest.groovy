package com.etendoerp.gradle.tests.distribution

import com.etendoerp.gradle.tests.EtendoSpecification
import spock.lang.Ignore
import spock.lang.TempDir
import spock.lang.Title

@Title("TODO: Add missing test")
@Ignore
class ApplyModuleBuildTest extends EtendoSpecification {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    def "missing test"() {
        expect: "TODO: create tests"
        true
    }
}
