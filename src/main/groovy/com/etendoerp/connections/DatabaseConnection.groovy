package com.etendoerp.connections

import groovy.sql.GroovyRowResult
import groovy.sql.Sql
import org.gradle.api.Project

import javax.sql.DataSource
import java.sql.*

class DatabaseConnection {

    Project project
    DataSource dataSource

    DatabaseConnection(Project project) {
        this.project = project
    }

    boolean loadDatabaseConnection(){
        return loadDatabaseConnection(false)
    }
    boolean loadSystemDatabaseConnection(){
        return loadDatabaseConnection(true)
    }

    boolean loadDatabaseConnection(boolean adminConnection){
        // Load the database properties
        DatabaseProperties databaseProperties = new DatabaseProperties(project)
        if (!databaseProperties.loadDatabaseProperties(adminConnection)) {
            project.logger.info("The database properties could not be loaded.")
            return false
        }

        this.dataSource = DBCPDataSourceFactory.getDatasource(databaseProperties)

        return validateConnection(databaseProperties)

    }

    Connection getConnection() {
        return this.dataSource.getConnection()
    }

    boolean validateConnection(DatabaseProperties databaseProperties) {
        try {
            def sql = new Sql(dataSource)
            String validationQuery = getValidationQuery(databaseProperties)
            def x = sql.rows(validationQuery)
            return true
        } catch (SQLException e) {
            project.logger.info("* WARNING: Etendo plugin database connection. The connection is not valid.")
            project.logger.info("* MESSAGE: ${e.message}")
            return false
        }
    }

    static String getValidationQuery(DatabaseProperties databaseProperties) {
        // Default POSTGRE query
        String query = "select 1"

        if (databaseProperties.databaseType == DatabaseType.ORACLE) {
            query = "select 1 from dual"
        }
        return query
    }

    List<GroovyRowResult> executeSelectQuery(String query, List<Object> params = []) {
        List<GroovyRowResult> rowResult = []
        def sql = new Sql(dataSource)
        try {
            rowResult = sql.rows(query, params)
        } catch (SQLException e) {
            project.logger.info("* WARNING: The query '${query}' could not be executed.")
            project.logger.info("* MESSAGE: ${e.message}")
            throw e
        } finally {
            try {
                sql.close()
            } catch (SQLException e) {
                project.logger.info("* WARNING: The connection could not be closed.")
                project.logger.info("* MESSAGE: ${e.message}")
                throw e
            }
        }
        return rowResult
    }

    List<GroovyRowResult> executeSelectQuery(Connection connection, String query, List<Object> params = []) {
        List<GroovyRowResult> rowResult = []
        def sql = new Sql(connection: connection)
        try {
            rowResult = sql.rows(query, params)
        } catch (SQLException e) {
            project.logger.info("* WARNING: The query '${query}' could not be executed.")
            project.logger.info("* MESSAGE: ${e.message}")
            throw e
        }
        return rowResult
    }

    void insert(Connection connection, String query, List<Object> params) {
        def sql = new Sql(connection: connection)
        try {
            sql.executeInsert(query, params)
        } catch (SQLException e) {
            project.logger.info("* WARNING: The query '${query}' could not be executed.")
            project.logger.info("* MESSAGE: ${e.message}")
            throw e
        }
    }

    void update(Connection connection, String query, List<Object> params) {
        def sql = new Sql(connection: connection )
        try {
            sql.executeUpdate(query, params)
        } catch (SQLException e) {
            project.logger.info("* WARNING: The query '${query}' could not be executed.")
            project.logger.info("* MESSAGE: ${e.message}")
            throw e
        }
    }
}
