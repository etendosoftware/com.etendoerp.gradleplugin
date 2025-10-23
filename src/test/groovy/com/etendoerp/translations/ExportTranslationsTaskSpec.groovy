package com.etendoerp.translations

import com.etendoerp.connections.DatabaseConnection
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Subject

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

class ExportTranslationsTaskSpec extends Specification {

    @Subject
    ExportTranslationsTask task
    Project project
    Connection mockConnection
    
    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.create('exportTranslations', ExportTranslationsTask)
        
        // Create mock connection
        mockConnection = Mock(Connection)
        
        // Setup basic directory structure
        def testDir = new File(project.projectDir, 'modules')
        testDir.mkdirs()
    }

    def "should fail when modules parameter is empty"() {
        given:
        // Remove modules property but keep source.path
        project.ext.'modules' = ''
        project.ext.'source.path' = '/test/path'

        when:
        task.export()

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("'modules' parameter is required")
    }

    def "should fail when source.path is not set"() {
        given:
        // Don't set source.path property
        project.ext.'modules' = 'core'

        when:
        task.export()

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message.contains("'source.path' property is required")
    }

    def "should handle successful export workflow"() {
        given:
        // Setup required properties
        project.ext.'source.path' = '/test/path'
        project.ext.'modules' = 'core'
        
        // Mock DatabaseConnection using global mock
        GroovyMock(DatabaseConnection, global: true)
        def mockDbConn = Mock(DatabaseConnection)
        new DatabaseConnection(_) >> mockDbConn
        mockDbConn.loadDatabaseConnection() >> true
        mockDbConn.getConnection() >> mockConnection
        
        // Mock language initialization check
        def mockStmt = Mock(PreparedStatement)
        def mockResultSet = Mock(ResultSet)
        mockConnection.prepareStatement(_) >> mockStmt
        mockStmt.executeQuery() >> mockResultSet
        mockStmt.executeUpdate() >> 1
        mockResultSet.next() >>> [true, true, false] // Multiple calls for different queries
        mockResultSet.getInt('count') >> 5 // Language is initialized
        mockResultSet.getString(_) >> 'test-value'
        
        // Create test directories
        def tempDir = File.createTempDir()
        project.ext.'source.path' = tempDir.absolutePath
        def testModulesDir = new File(tempDir, 'modules')
        testModulesDir.mkdirs()

        when:
        task.export()

        then:
        noExceptionThrown()
        
        cleanup:
        tempDir.deleteDir()
    }

    def "should detect when language is initialized"() {
        given:
        // Mock DatabaseConnection
        GroovyMock(DatabaseConnection, global: true)
        def mockDbConn = Mock(DatabaseConnection)
        new DatabaseConnection(_) >> mockDbConn
        mockDbConn.loadDatabaseConnection() >> true
        mockDbConn.getConnection() >> mockConnection
        
        def mockStmt = Mock(PreparedStatement)
        def mockResultSet = Mock(ResultSet)
        mockConnection.prepareStatement(_) >> mockStmt
        mockStmt.executeQuery() >> mockResultSet
        mockResultSet.next() >> true
        mockResultSet.getInt('count') >> 5 // Language has translations

        when:
        def result = task.isLanguageInitialized(mockConnection, 'es_ES')

        then:
        result == true
    }

    def "should detect when language is not initialized"() {
        given:
        def mockStmt = Mock(PreparedStatement)
        def mockResultSet = Mock(ResultSet)
        mockConnection.prepareStatement(_) >> mockStmt
        mockStmt.executeQuery() >> mockResultSet
        mockResultSet.next() >> true
        mockResultSet.getInt('count') >> 0 // No translations

        when:
        def result = task.isLanguageInitialized(mockConnection, 'fr_FR')

        then:
        result == false
    }
}