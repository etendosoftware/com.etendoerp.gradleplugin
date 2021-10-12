package com.etendoerp.gradle.tests.docker

import com.etendoerp.gradle.tests.EtendoSpecification
import spock.lang.TempDir

class TestModuleInstallTest extends EtendoSpecification {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    def "missing test"() {
        expect: "TODO: create tests"
        false
    }
}
