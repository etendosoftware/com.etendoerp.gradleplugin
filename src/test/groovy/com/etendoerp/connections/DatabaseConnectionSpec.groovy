package com.etendoerp.connections

import groovy.sql.Sql
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.SQLException

class DatabaseConnectionSpec extends Specification {

    def cleanup() {
        GroovySystem.metaClassRegistry.removeMetaClass(Sql)
    }

    def "getValidationQuery returns oracle dual query when needed"() {
        given:
        def props = Stub(DatabaseProperties) {
            getDatabaseType() >> DatabaseType.ORACLE
        }

        expect:
        DatabaseConnection.getValidationQuery(props) == "select 1 from dual"
    }

    def "validateConnection returns true on successful query"() {
        given:
        def project = ProjectBuilder.builder().build()
        def db = new DatabaseConnection(project)
        db.dataSource = Mock(DataSource)
        def props = Stub(DatabaseProperties) {
            getDatabaseType() >> DatabaseType.POSTGRE
        }

        GroovyMock(Sql, global: true)
        def sql = Mock(Sql)
        new Sql(_ as DataSource) >> sql
        sql.rows("select 1") >> []

        expect:
        db.validateConnection(props)
    }

    def "validateConnection returns false when query fails"() {
        given:
        def project = ProjectBuilder.builder().build()
        def db = new DatabaseConnection(project)
        db.dataSource = Mock(DataSource)
        def props = Stub(DatabaseProperties) {
            getDatabaseType() >> DatabaseType.POSTGRE
        }

        GroovyMock(Sql, global: true)
        def sql = Mock(Sql)
        new Sql(_ as DataSource) >> sql
        sql.rows("select 1") >> { throw new SQLException("boom") }

        expect:
        !db.validateConnection(props)
    }

    def "executeSelectQuery returns rows and closes sql"() {
        given:
        def project = ProjectBuilder.builder().build()
        def db = new DatabaseConnection(project)
        db.dataSource = Mock(DataSource)

        GroovyMock(Sql, global: true)
        def sql = Mock(Sql)
        new Sql(_ as DataSource) >> sql
        sql.rows("select * from test", []) >> [[id: 1]]

        when:
        def result = db.executeSelectQuery("select * from test", [])

        then:
        result.size() == 1
        1 * sql.close()
    }

    def "executeSelectQuery with connection delegates to sql rows"() {
        given:
        def project = ProjectBuilder.builder().build()
        def db = new DatabaseConnection(project)
        def connection = Mock(Connection)

        GroovyMock(Sql, global: true)
        def sql = Mock(Sql)
        new Sql(connection: connection) >> sql
        sql.rows("select * from test", []) >> [[id: 1]]

        when:
        def result = db.executeSelectQuery(connection, "select * from test", [])

        then:
        result.size() == 1
    }
}
