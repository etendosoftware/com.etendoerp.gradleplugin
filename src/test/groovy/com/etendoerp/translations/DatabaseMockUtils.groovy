package com.etendoerp.translations

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

/**
 * Utility class for creating database mocks in translation tests.
 * 
 * Provides pre-configured mock objects and common test data scenarios
 * for database operations used in translation tasks.
 */
class DatabaseMockUtils {

    /**
     * Creates a mock connection with common translation-related prepared statements.
     * 
     * @param testData Map containing test data for different queries
     * @return Mock Connection object
     */
    static Connection createMockConnection(Map<String, Object> testData = [:]) {
        def mockConnection = Mock(Connection)
        
        // Default test data
        def defaultData = [
            languageCount: 5,
            languageId: '192',
            moduleLanguage: 'en_US',
            systemVersion: '23.1.0',
            contributors: 'Test Contributor',
            uuid: 'test-uuid-123',
            modules: [
                [moduleId: '0', javaPackage: null],
                [moduleId: '123', javaPackage: 'com.etendoerp.test']
            ],
            trlTables: ['AD_ELEMENT_TRL', 'AD_COLUMN_TRL'],
            trlColumns: [
                [columnName: 'NAME'],
                [columnName: 'DESCRIPTION']
            ],
            referenceDataTables: [
                [moduleId: '0', tableName: 'AD_REF_LIST', tableId: '104', moduleLanguage: 'en_US', javaPackage: null],
                [moduleId: '123', tableName: 'AD_MESSAGE', tableId: '105', moduleLanguage: 'es_ES', javaPackage: 'com.etendoerp.test']
            ],
            columnExists: true,
            centrallyMaintained: true,
            updateRows: 1
        ]
        
        // Merge test data with defaults
        def data = defaultData + testData
        
        mockConnection.prepareStatement(!null) >> { String sql ->
            def mockStmt = Mock(PreparedStatement)
            def mockResultSet = Mock(ResultSet)
            
            // Configure based on SQL pattern
            if (sql.contains('AD_ELEMENT_TRL') && sql.contains('COUNT')) {
                // Language initialization check
                mockStmt.executeQuery() >> mockResultSet
                mockResultSet.next() >> true
                mockResultSet.getInt('count') >> data.languageCount
                
            } else if (sql.contains('AD_LANGUAGE_ID') && sql.contains('AD_LANGUAGE')) {
                // Get language ID
                mockStmt.executeQuery() >> mockResultSet
                mockResultSet.next() >> (data.languageId != null)
                if (data.languageId) {
                    mockResultSet.getString('AD_LANGUAGE_ID') >> data.languageId
                }
                
            } else if (sql.contains('AD_LANGUAGE') && sql.contains('SELECT AD_LANGUAGE')) {
                // Get module language
                mockStmt.executeQuery() >> mockResultSet
                mockResultSet.next() >> true
                mockResultSet.getString('language') >> data.moduleLanguage
                
            } else if (sql.contains('OB_VERSION')) {
                // Get system version
                mockStmt.executeQuery() >> mockResultSet
                mockResultSet.next() >> true
                mockResultSet.getString('OB_VERSION') >> data.systemVersion
                
            } else if (sql.contains('TranslatedBy')) {
                // Get contributors
                mockStmt.executeQuery() >> mockResultSet
                mockResultSet.next() >> (data.contributors != null)
                if (data.contributors) {
                    mockResultSet.getString('TranslatedBy') >> data.contributors
                }
                
            } else if (sql.contains('GET_UUID')) {
                // Generate UUID
                mockStmt.executeQuery() >> mockResultSet
                mockResultSet.next() >> true
                mockResultSet.getString(1) >> data.uuid
                
            } else if (sql.contains('ad_module_id') && sql.contains('JAVAPACKAGE')) {
                // Get modules
                mockStmt.executeQuery() >> mockResultSet
                def modules = data.modules as List
                def nextCalls = modules.collect { true } + [false]
                mockResultSet.next() >>> nextCalls
                
                if (modules) {
                    mockResultSet.getString('moduleId') >>> modules.collect { it.moduleId }
                    mockResultSet.getString('javaPackage') >>> modules.collect { it.javaPackage }
                }
                
            } else if (sql.contains('tablename') && sql.contains('%trl')) {
                // Get TRL tables
                mockStmt.executeQuery() >> mockResultSet
                def tables = data.trlTables as List
                def nextCalls = tables.collect { true } + [false]
                mockResultSet.next() >>> nextCalls
                
                if (tables) {
                    mockResultSet.getString('tablename') >>> tables
                }
                
            } else if (sql.contains('ColumnName') && sql.contains('AD_Reference_ID')) {
                // Get translatable columns
                mockStmt.executeQuery() >> mockResultSet
                def columns = data.trlColumns as List
                def nextCalls = columns.collect { true } + [false]
                mockResultSet.next() >>> nextCalls
                
                if (columns) {
                    mockResultSet.getString('columnName') >>> columns.collect { it.columnName }
                }
                
            } else if (sql.contains('AD_DATASET')) {
                // Get reference data tables
                mockStmt.executeQuery() >> mockResultSet
                def tables = data.referenceDataTables as List
                def nextCalls = tables.collect { true } + [false]
                mockResultSet.next() >>> nextCalls
                
                if (tables) {
                    mockResultSet.getString('moduleId') >>> tables.collect { it.moduleId }
                    mockResultSet.getString('tablename') >>> tables.collect { it.tableName }
                    mockResultSet.getString('tableId') >>> tables.collect { it.tableId }
                    mockResultSet.getString('moduleLanguage') >>> tables.collect { it.moduleLanguage }
                    mockResultSet.getString('javaPackage') >>> tables.collect { it.javaPackage }
                }
                
            } else if (sql.contains('count(*)')) {
                // Generic count query (column existence, centrally maintained)
                mockStmt.executeQuery() >> mockResultSet
                mockResultSet.next() >> true
                
                if (sql.contains('ISCENTRALLYMAINTAINED')) {
                    mockResultSet.getInt('count') >> (data.centrallyMaintained ? 1 : 0)
                } else {
                    mockResultSet.getInt('count') >> (data.columnExists ? 1 : 0)
                }
                
            } else if (sql.contains('UPDATE') || sql.contains('INSERT')) {
                // Update/Insert operations
                mockStmt.executeUpdate() >> data.updateRows
                
            } else if (sql.contains('AD_LANGUAGE_CREATE')) {
                // Language creation procedure
                mockStmt.executeQuery() >> mockResultSet
                mockResultSet.next() >> true
                mockResultSet.getString(1) >> 'Language created successfully'
            }
            
            // Common cleanup methods
            mockResultSet.close() >> { }
            mockStmt.close() >> { }
            
            return mockStmt
        }
        
        return mockConnection
    }

    /**
     * Creates test data for a language that is already initialized.
     */
    static Map<String, Object> getInitializedLanguageData() {
        return [
            languageCount: 5,
            languageId: '192'
        ]
    }

    /**
     * Creates test data for a language that is not initialized.
     */
    static Map<String, Object> getUninitializedLanguageData() {
        return [
            languageCount: 0,
            languageId: '192'
        ]
    }

    /**
     * Creates test data for a language that doesn't exist in the system.
     */
    static Map<String, Object> getNonExistentLanguageData() {
        return [
            languageCount: 0,
            languageId: null
        ]
    }

    /**
     * Creates test data with multiple modules.
     */
    static Map<String, Object> getMultipleModulesData() {
        return [
            modules: [
                [moduleId: '0', javaPackage: null],
                [moduleId: '123', javaPackage: 'com.etendoerp.test'],
                [moduleId: '456', javaPackage: 'com.etendoerp.another']
            ]
        ]
    }

    /**
     * Creates test data with multiple translation tables.
     */
    static Map<String, Object> getMultipleTrlTablesData() {
        return [
            trlTables: [
                'AD_ELEMENT_TRL',
                'AD_COLUMN_TRL',
                'AD_TABLE_TRL',
                'AD_FIELD_TRL'
            ]
        ]
    }

    /**
     * Creates test data with extensive translatable columns.
     */
    static Map<String, Object> getExtensiveTrlColumnsData() {
        return [
            trlColumns: [
                [columnName: 'NAME'],
                [columnName: 'DESCRIPTION'],
                [columnName: 'HELP'],
                [columnName: 'PLACEHOLDER'],
                [columnName: 'MSGTIP']
            ]
        ]
    }

    /**
     * Creates test data for reduced version (excluding help fields).
     */
    static Map<String, Object> getReducedVersionTrlColumnsData() {
        return [
            trlColumns: [
                [columnName: 'NAME'],
                [columnName: 'PLACEHOLDER']
                // DESCRIPTION, HELP, MSGTIP excluded
            ]
        ]
    }

    /**
     * Creates test data for reference data tables.
     */
    static Map<String, Object> getReferenceDataTablesData() {
        return [
            referenceDataTables: [
                [moduleId: '0', tableName: 'AD_REF_LIST', tableId: '104', moduleLanguage: 'en_US', javaPackage: null],
                [moduleId: '0', tableName: 'AD_MESSAGE', tableId: '105', moduleLanguage: 'en_US', javaPackage: null],
                [moduleId: '123', tableName: 'CUSTOM_TABLE', tableId: '999', moduleLanguage: 'es_ES', javaPackage: 'com.etendoerp.custom']
            ]
        ]
    }

    /**
     * Creates test data for tables without certain columns.
     */
    static Map<String, Object> getTablesWithoutColumnsData() {
        return [
            columnExists: false,
            centrallyMaintained: false
        ]
    }

    /**
     * Creates a simple mock ResultSet with predefined data.
     * 
     * @param data List of maps representing rows and columns
     * @return Mock ResultSet
     */
    static ResultSet createMockResultSet(List<Map<String, Object>> data) {
        def mockResultSet = Mock(ResultSet)
        
        if (data.isEmpty()) {
            mockResultSet.next() >> false
            return mockResultSet
        }
        
        def nextCalls = data.collect { true } + [false]
        mockResultSet.next() >>> nextCalls
        
        // Configure getters for each column that appears in the data
        def allColumns = data.collectMany { it.keySet() }.unique()
        
        allColumns.each { column ->
            def values = data.collect { it[column] }
            
            mockResultSet.getString(column) >>> values
            mockResultSet.getInt(column) >>> values.collect { 
                it instanceof Integer ? it : (it?.toString()?.isInteger() ? Integer.parseInt(it.toString()) : 0)
            }
        }
        
        mockResultSet.close() >> { }
        
        return mockResultSet
    }

    /**
     * Creates a mock PreparedStatement that returns a specific ResultSet.
     */
    static PreparedStatement createMockPreparedStatement(ResultSet resultSet = null) {
        def mockStmt = Mock(PreparedStatement)
        
        if (resultSet) {
            mockStmt.executeQuery() >> resultSet
        }
        
        mockStmt.executeUpdate() >> 1
        mockStmt.close() >> { }
        
        return mockStmt
    }
}