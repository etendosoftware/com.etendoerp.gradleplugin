package com.etendoerp.gradle.tests.installation

import com.etendoerp.gradle.tests.EtendoSpecification
import spock.lang.Ignore
import spock.lang.TempDir
import spock.lang.Title

@Title("TODO: Add missing test")
@Ignore
class CleanTempModulesTest extends EtendoSpecification {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    def "missing test"() {
        expect: "TODO: create tests"
        1 == 1
    }
}
