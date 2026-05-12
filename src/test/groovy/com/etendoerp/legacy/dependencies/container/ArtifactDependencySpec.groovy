package com.etendoerp.legacy.dependencies.container

import spock.lang.Specification

class ArtifactDependencySpec extends Specification {

    def "buildModuleName appends group when artifact is a module suffix"() {
        expect:
        ArtifactDependency.buildModuleName('com.etendoerp', 'analytics.exporter') == 'com.etendoerp.analytics.exporter'
    }

    def "buildModuleName uses artifact when artifact is already the full module package"() {
        expect:
        ArtifactDependency.buildModuleName('com.etendoerp', 'com.etendoerp.analytics.exporter') == 'com.etendoerp.analytics.exporter'
    }

    def "buildModuleName uses artifact when artifact equals group"() {
        expect:
        ArtifactDependency.buildModuleName('org.openbravo', 'org.openbravo') == 'org.openbravo'
    }
}
