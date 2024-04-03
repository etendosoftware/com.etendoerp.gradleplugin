package com.etendoerp

import com.etendoerp.connections.DatabaseConnection
import org.gradle.api.Project

class DBDependenciesLoader {
    static void load(Project project) {
        def databaseConnection = new DatabaseConnection(project)
        try {
            def validConnection = databaseConnection.loadDatabaseConnection()
            if (!validConnection) {
                project.logger.info("* The connection with the database could not be established. Skipping version consistency verification.")
            } else {
                def rowResult = databaseConnection.executeSelectQuery("select * from etdep_dependency")
                rowResult.forEach { row ->
                    if (row.format == "J") {
                        project.dependencies.add("implementation", "${row.depgroup}:${row.artifact}:${row.version}")
                    } else {
                        project.dependencies.add("moduleDeps", "${row.depgroup}:${row.artifact}:${row.version}@zip", { transitive = false })
                    }
                }
            }
        } catch (Exception e) {
            project.logger.debug("Error connecting to database: ${e.getMessage()}")
        }
    }
}
