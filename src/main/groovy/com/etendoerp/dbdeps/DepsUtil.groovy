package com.etendoerp.dbdeps

import com.etendoerp.connections.DatabaseConnection
import org.gradle.api.Project

/**
 * Utility class for handling dynamic dependencies in a Gradle project.
 */
class DepsUtil {
    /**
     * Checks if the dynamic dependencies plugin is installed in the project.
     * It tries to establish a connection to the database and fetches dependencies.
     * If it can fetch dependencies, it means the dynamic dependencies plugin is installed.
     *
     * @param project The Gradle project in which to check for the dynamic dependencies plugin.
     * @return true if the dynamic dependencies plugin is installed, false otherwise.
     */
    static boolean isDynDepsInstalled(Project project) {
        try {
            // Create a new database connection
            def databaseConnection = new DatabaseConnection(project)
            // Try to establish a connection to the database
            def validConnection = databaseConnection.loadDatabaseConnection()
            // If the connection could not be established, log an info message and return false
            if (!validConnection) {
                project.logger.info("* The connection with the database could not be established. Skipping version consistency verification.")
                return false
            }
            def exists = databaseConnection.executeSelectQuery("SELECT COUNT(*) AS count FROM user_tables WHERE table_name = 'ETDEP_DEPENDENCY'")
            if(exists == null || exists.size() == 0) {
                return false
            }
            // Execute a select query to fetch dependencies from the database
            def rowResult = databaseConnection.executeSelectQuery("select depgroup, artifact, version, format from etdep_dependency")
            // If the query returned null or empty, return false
            return rowResult != null && rowResult.size() > 0
        } catch (Exception e) {
            // Log any exceptions that occur while connecting to the database
            project.logger.debug("Error connecting to database: ${e.getMessage()}")
            return false
        }
    }
}
