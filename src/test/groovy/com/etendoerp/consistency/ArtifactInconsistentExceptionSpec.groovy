package com.etendoerp.consistency

import spock.lang.Specification

class ArtifactInconsistentExceptionSpec extends Specification {

    def "message is preserved"() {
        when:
        def ex = new ArtifactInconsistentException("boom")

        then:
        ex.message == "boom"
    }
}
