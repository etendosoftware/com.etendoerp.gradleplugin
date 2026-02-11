package com.etendoerp.connections

import org.apache.commons.dbcp2.BasicDataSource
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class DBCPDataSourceFactorySpec extends Specification {

    @TempDir
    Path tempDir

    def "getDatasource configures basic datasource fields"() {
        given:
        def project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()
        def props = new DatabaseProperties(project)
        props.driver = "org.postgresql.Driver"
        props.url = "jdbc:postgresql://localhost:5432"
        props.sid = "etendo"
        props.user = "appuser"
        props.password = "apppass"

        when:
        def ds = DBCPDataSourceFactory.getDatasource(props)

        then:
        ds instanceof BasicDataSource
        ds.driverClassName == "org.postgresql.Driver"
        ds.url == "jdbc:postgresql://localhost:5432/etendo"
        ds.username == "appuser"
        ds.password == "apppass"
    }
}
