package com.etendoerp.dbdeps

import com.etendoerp.connections.DatabaseConnection
import groovy.sql.GroovyRowResult
import org.gradle.api.Project

/**
 * This class is responsible for loading dependencies from a database into a Gradle project.
 */
class DBDepsLoader {
    /**
     * This method loads dependencies from a database into a Gradle project.
     * It first checks if the dynamic dependencies plugin is installed.
     * If it is, it establishes a connection to the database.
     * If the connection is successful, it executes a select query to fetch dependencies from the database.
     * It then adds these dependencies to the project.
     *
     * @param project The Gradle project to which the dependencies will be added.
     */
    static void load(Project project) {
        // Check if the dynamic dependencies plugin is installed
        if (!DepsUtil.isDynDepsInstalled(project)) {
            return
        }
        // Create a new database connection
        def databaseConnection = new DatabaseConnection(project)
        // Try to establish a connection to the database
        def validConnection = databaseConnection.loadDatabaseConnection()
        // If the connection could not be established, log an info message and return
        if (!validConnection) {
            project.logger.info("* The connection with the database could not be established. Skipping version consistency verification.")
            return
        }
        // Execute a select query to fetch dependencies from the database
        List<GroovyRowResult> rowResult = databaseConnection.executeSelectQuery("select depgroup, artifact, version, format from etdep_dependency")
        // If the query returned null, return
        if (rowResult == null) {
            return
        }
        // For each row in the result, add the corresponding dependency to the project
        rowResult.forEach { row ->
            if (row.format == "J") {
                project.dependencies.add("implementation", "${row.depgroup}:${row.artifact}:${row.version}")
            } else {
                project.dependencies.add("moduleDeps", "${row.depgroup}:${row.artifact}:${row.version}@zip", { transitive = false })
            }
        }
    }
}
