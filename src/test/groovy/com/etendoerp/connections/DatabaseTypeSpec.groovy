package com.etendoerp.connections

import spock.lang.Specification

class DatabaseTypeSpec extends Specification {

    def "parseType resolves known types and defaults to UNDEFINED"() {
        expect:
        DatabaseType.parseType("POSTGRE") == DatabaseType.POSTGRE
        DatabaseType.parseType("ORACLE") == DatabaseType.ORACLE
        DatabaseType.parseType("UNKNOWN") == DatabaseType.UNDEFINED
    }

    def "containsType matches enum values"() {
        expect:
        DatabaseType.containsType("POSTGRE")
        DatabaseType.containsType("ORACLE")
        !DatabaseType.containsType("NOPE")
    }
}
