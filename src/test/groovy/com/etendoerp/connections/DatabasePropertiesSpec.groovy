package com.etendoerp.connections

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class DatabasePropertiesSpec extends Specification {

    @TempDir
    Path tempDir

    def "loadDatabaseProperties reads values and resolves admin credentials"() {
        given:
        def project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()
        def configDir = new File(project.rootDir, "config")
        configDir.mkdirs()
        def propsFile = new File(configDir, DatabaseProperties.PROPERTIES_FILE)
        propsFile.text = """
            bbdd.rdbms=POSTGRE
            bbdd.driver=org.postgresql.Driver
            bbdd.url=jdbc:postgresql://localhost:5432
            bbdd.sid=etendo
            bbdd.user=appuser
            bbdd.password=apppass
            bbdd.systemUser=admin
            bbdd.systemPassword=adminpass
        """.stripIndent().trim() + "\n"

        def dbProps = new DatabaseProperties(project)

        when:
        def okUser = dbProps.loadDatabaseProperties(false)

        then:
        okUser
        dbProps.user == "appuser"
        dbProps.password == "apppass"
        dbProps.databaseType == DatabaseType.POSTGRE
        dbProps.getDatabaseUrl() == "jdbc:postgresql://localhost:5432/etendo"

        when:
        def okAdmin = dbProps.loadDatabaseProperties(true)

        then:
        okAdmin
        dbProps.user == "admin"
        dbProps.password == "adminpass"
    }

    def "loadDatabaseProperties returns false when properties file is missing"() {
        given:
        def project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()
        def dbProps = new DatabaseProperties(project)

        when:
        def ok = dbProps.loadDatabaseProperties()

        then:
        !ok
    }

    def "loadProperties throws when a required property is missing"() {
        given:
        def project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()
        def dbProps = new DatabaseProperties(project)
        dbProps.propertiesFileLocation = new File(project.rootDir, "config" + File.separator + DatabaseProperties.PROPERTIES_FILE)
        def props = new Properties()
        props.setProperty(DatabaseProperties.RDBMS_PROPERTY, "POSTGRE")
        props.setProperty(DatabaseProperties.DRIVER_PROPERTY, "org.postgresql.Driver")
        dbProps.propertiesFile = props

        when:
        dbProps.loadProperties([DatabaseProperties.URL_PROPERTY])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains(DatabaseProperties.URL_PROPERTY)
    }
}
