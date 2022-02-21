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

    boolean loadDatabaseConnection() {
        // Load the database properties
        DatabaseProperties databaseProperties = new DatabaseProperties(project)
        if (!databaseProperties.loadDatabaseProperties()) {
            project.logger.error("The database properties could not be loaded.")
            return false
        }

        this.dataSource = DBCPDataSourceFactory.getDatasource(databaseProperties)

        return validateConnection(databaseProperties)

    }

    boolean validateConnection(DatabaseProperties databaseProperties) {
        boolean isValid = false
        try {
            def sql = new Sql(dataSource)
            String validationQuery = getValidationQuery(databaseProperties)
            def x = sql.rows(validationQuery)
            isValid = true
        } catch (SQLException e) {
            project.logger.error("* Error validating the connection.")
            project.logger.error("* Error: ${e.message}")
            isValid = false
        }
        return isValid
    }

    static String getValidationQuery(DatabaseProperties databaseProperties) {
        String query = ""
        switch (databaseProperties.databaseType) {
            case DatabaseType.POSTGRE:
                query = "select 1;"
                break
            case DatabaseType.ORACLE:
                query = "select 1 from dual;"
                break
        }
        return query
    }

    List<GroovyRowResult> executeSelectQuery(String query) {
        List<GroovyRowResult> rowResult = []
        def sql = new Sql(dataSource)
        try {
            rowResult = sql.rows(query)
        } catch (SQLException e) {
            project.logger.error("* Error executing the query.")
            project.logger.error("* Error: ${e.message}")
        } finally {
            try {
               sql.close()
            } catch (SQLException e) {
                project.logger.error("* Error closing connections.")
                project.logger.error("* Error: ${e.message}")
            }
        }
        return rowResult
    }


}
