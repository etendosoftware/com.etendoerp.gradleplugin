package com.etendoerp.gradle.tests.distribution

import com.etendoerp.gradle.tests.EtendoSpecification
import spock.lang.PendingFeature
import spock.lang.TempDir
import spock.lang.Title

@Title("TODO: Add missing test")
class RegisterModuleTest extends EtendoSpecification {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    @PendingFeature
    // Write tests when tasks allow configuring a different nexus URL, to point to a mock server. Or find out how to mock request to the default URL.
    def "missing test"() {
        expect: "TODO: create tests"
        true
    }
}
