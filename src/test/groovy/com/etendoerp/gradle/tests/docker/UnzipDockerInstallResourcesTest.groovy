package com.etendoerp.gradle.tests.docker

import com.etendoerp.gradle.tests.EtendoSpecification
import spock.lang.TempDir
import spock.lang.Title

@Title("TODO: Add missing test")
class UnzipDockerInstallResourcesTest extends EtendoSpecification {
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
