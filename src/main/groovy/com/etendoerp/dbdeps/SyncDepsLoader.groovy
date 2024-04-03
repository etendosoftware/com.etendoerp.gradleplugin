package com.etendoerp.dbdeps

import com.etendoerp.connections.DatabaseConnection
import org.gradle.api.GradleException
import org.gradle.api.Project
import com.etendoerp.legacy.dependencies.ResolverDependencyLoader
import java.nio.file.Files
import java.sql.Connection

/**
 * SyncDepsLoader is a utility class for synchronizing dependencies in a Gradle project with a database.
 */
class SyncDepsLoader {
    /**
     * The load method is responsible for registering a new task in the Gradle project that syncs the dependencies.
     * It first checks if the dynamic dependencies plugin is installed and if the task name is "dependencies.sync".
     * If these conditions are met, it registers a new task that reads the dependencies from the build.gradle file,
     * checks them against the database, and updates the database if necessary.
     *
     * @param project The Gradle project to which the dependencies will be added.
     */
    static void load(Project project) {
        // Check if the dynamic dependencies plugin is installed and if the task name is "dependencies.sync"
        if (!DepsUtil.isDynDepsInstalled(project) && !project.gradle.startParameter.taskNames.contains("dependencies.sync")) {
            return
        }
        // Register a new task in the Gradle project
        project.tasks.register("dependencies.sync") {
            doLast {
                // Define the build.gradle file
                File gradleBuildFile = new File(ResolverDependencyLoader.sourcePath, 'build.gradle')
                // Check if the build.gradle file exists
                if (!gradleBuildFile.exists()) {
                    project.logger.error("build.gradle file not found")
                    return
                }

                boolean insideDependenciesBlock = false
                boolean insideDepBlock = false
                // Define the regex patterns for matching dependencies in the build.gradle file
                def startDependenciesBlockRegex = /^\s*dependencies\s*\{.*$/
                def endDependenciesBlockRegex = /^\s*\}.*$/
                def dependencyRegex = /(implementation|moduleDeps)\s*(?:\(\s*)?['"]([^:'"]+):([^:'"]+):([^:'"]+)['"]\s*\)?/
                def dependencyRegex2 = /(implementation|moduleDeps)\s+\(?\s*group:\s*['"]([^'"]+)['"],\s*name:\s*['"]([^'"]+)['"],\s*version:\s*['"]([^'"]+)['"]\s*\)?/
                def dependencyRegex3 = /(implementation|moduleDeps)\s*\(\s*group:\s*['"]([^'"]+)['"],\s*name:\s*['"]([^'"]+)['"],\s*version:\s*['"]([^'"]+)['"]\s*\)/
                def endImplementationsBlockRegex = /}/
                def commentedLine = /\/\//
                List<Dep> depsInFile = []

                // Read the build.gradle file line by line and match dependencies
                String newGradleFile = gradleBuildFile.readLines().collect { line ->
                    // Skip commented lines
                    if (line =~ commentedLine) {
                        return line
                    }
                    // Check if the line starts a dependencies block
                    if (line.matches(startDependenciesBlockRegex)) {
                        insideDependenciesBlock = true
                    } else if (insideDependenciesBlock && !insideDepBlock && line.matches(endDependenciesBlockRegex)) {
                        // Check if the line ends a dependencies block
                        insideDependenciesBlock = false
                    }
                    // Check if the line ends an implementations block
                    if (insideDepBlock && line =~ endImplementationsBlockRegex) {
                        line = "//" + line
                        insideDepBlock = false
                    } else if (insideDepBlock) {
                        // Comment out the line if it's inside an implementations block
                        line = "//" + line
                    }

                    // Check if the line matches a dependency
                    if (insideDependenciesBlock && (line =~ dependencyRegex || line =~ dependencyRegex2 || line =~ dependencyRegex3)) {
                        // Get the dependency group, artifact and version from regexp
                        def matcher = line =~ dependencyRegex ?: line =~ dependencyRegex2 ?: line =~ dependencyRegex3
                        if (matcher[0].size() != 5) {
                            return line
                        }
                        def type = matcher[0][1].toString()
                        def group = matcher[0][2].toString()
                        def artifact = matcher[0][3].toString()
                        def version = matcher[0][4].toString()
                        if (version.endsWith("@zip")) {
                            version = version.substring(0, version.length() - 4)
                        }
                        String depType
                        if (type == "moduleDeps") {
                            depType = "S"
                        } else {
                            depType = "J"
                        }
                        depsInFile.add(new Dep(group, artifact, version, depType))
                        if (line.contains("{")) {
                            insideDepBlock = true
                        }
                        if (line.contains("}")) {
                            insideDepBlock = false
                        }
                        line = "// " + line
                    }
                    return line
                }.join('\n')

                // Create a new database connection
                def databaseConnection = new DatabaseConnection(project)
                // Try to establish a connection to the database
                if (!databaseConnection.loadDatabaseConnection()) {
                    throw new Exception("* The connection with the database could not be established. Skipping version consistency verification.")
                }
                // Get the database connection
                def connection = databaseConnection.getConnection()
                try {
                    // Start a transaction
                    connection.setAutoCommit(false)
                    connection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED)
                    // For each dependency in the file, check it against the database and update the database if necessary
                    for (Dep dep : depsInFile) {
                        def result = databaseConnection.executeSelectQuery(connection, "select * from etdep_dependency where depgroup = ? and artifact = ?", [dep.group, dep.artifact])
                        if (result.size() == 0) {
                            databaseConnection.insert(connection, "insert into etdep_dependency (" +
                                    "etdep_dependency_id, created, createdby, updated, updatedby, ad_client_id, ad_org_id, depgroup, artifact, version, format" +
                                    ") values (get_uuid(), now(), '0', now(), '0', '0', '0', ?, ?, ?, ?)", [dep.group, dep.artifact, dep.version, dep.depType])
                            project.logger.info("Dependency ${dep.group}:${dep.artifact}:${dep.version} added to etdep_dependency table")
                        } else {
                            throw new Exception("*** Dependency inconsistency ${dep.group}:${dep.artifact} exists in dynamic dependencies. Check changes in build.gradle file ")
                        }
                    }
                    // Commit the transaction
                    connection.commit()
                } catch (Exception e) {
                    // Rollback the transaction in case of an error
                    connection.rollback()
                    throw new GradleException("🐞Error syncing dependencies: ${e.getMessage()}")
                } finally {
                    // Close the database connection
                    connection.setAutoCommit(true)
                    connection.close()
                }
                // If the build.gradle file has been modified, save the changes
                if (!gradleBuildFile.text.equals(newGradleFile)) {
                    def copyFile = getNonExistingCopyFile(gradleBuildFile)
                    Files.copy(gradleBuildFile.toPath(), copyFile.toPath());
                    gradleBuildFile.text = newGradleFile
                }
            }
        }
    }

    /**
     * This method is used to get a non-existing copy file for the build.gradle file.
     * It appends a number to the file name until it finds a file that does not exist.
     *
     * @param gradleBuildFile The build.gradle file for which to get a non-existing copy file.
     * @return A File object representing a non-existing copy file for the build.gradle file.
     */
    static File getNonExistingCopyFile(File gradleBuildFile) {
        def copyFile = new File(gradleBuildFile.absolutePath + ".copy")
        int i = 1
        while (copyFile.exists()) {
            copyFile = new File(gradleBuildFile.absolutePath + ".copy" + i)
            i++
        }
        return copyFile
    }
}
