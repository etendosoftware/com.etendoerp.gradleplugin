package com.etendoerp.gradle.tests.installation

import com.etendoerp.gradle.tests.EtendoSpecification
import spock.lang.TempDir

class ExpandCoreTest extends EtendoSpecification {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    def "missing test"() {
        expect: "TODO: create tests"
        1 == 2
    }
}
