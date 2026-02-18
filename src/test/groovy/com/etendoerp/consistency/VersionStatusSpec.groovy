package com.etendoerp.consistency

import spock.lang.Specification

class VersionStatusSpec extends Specification {

    def "containsType matches enum values"() {
        expect:
        VersionStatus.containsType("MAJOR")
        VersionStatus.containsType("MINOR")
        VersionStatus.containsType("EQUAL")
        VersionStatus.containsType("UNDEFINED")
        !VersionStatus.containsType("NOPE")
    }

    def "toString returns the backing type"() {
        expect:
        VersionStatus.MAJOR.toString() == "MAJOR"
    }
}
