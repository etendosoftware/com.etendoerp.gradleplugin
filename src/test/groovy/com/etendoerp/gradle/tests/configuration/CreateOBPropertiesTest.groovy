package com.etendoerp.gradle.tests.configuration

import com.etendoerp.gradle.tests.EtendoSpecification
import spock.lang.TempDir

class CreateOBPropertiesTest extends EtendoSpecification {
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
