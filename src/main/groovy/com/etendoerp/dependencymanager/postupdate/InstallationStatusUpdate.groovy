package com.etendoerp.dependencymanager.postupdate

import com.etendoerp.connections.DatabaseConnection
import com.etendoerp.consistency.EtendoArtifactsConsistencyContainer
import com.etendoerp.copilot.Constants
import groovy.sql.GroovyRowResult
import org.gradle.api.Project
import org.postgresql.util.PSQLException

class InstallationStatusUpdate {

    private static final String UPDATE_DATABASE_TASK = "update.database"
    private static final String INSTALLATION_STATUS_UPDATE_TASK = "installationStatusUpdate"

    static void load(Project project) {
        project.tasks.register(INSTALLATION_STATUS_UPDATE_TASK) {
            mustRunAfter(UPDATE_DATABASE_TASK)
            doLast {
                if (!depManagerIsInstalled(project)) {
                    return
                }
                StringBuilder infoMsg = new StringBuilder()
                infoMsg.append("\n\n************* DEPENDENCIES INSTALLATION STATUS UPDATE ************* \n")
                infoMsg.append("* The Dependency Manager module is installed in the system.\n")
                infoMsg.append("* Performing Installation Status Update.\n")
                infoMsg.append("*******************************************************************\n\n")
                project.logger.info(infoMsg.toString())

                DatabaseConnection conn = new DatabaseConnection(project)
                try {
                    conn.loadDatabaseConnection()
                    List<String> dependenciesToMarkAsInstalled = getDependenciesToMarkAsInstalled(conn)
                    if (!dependenciesToMarkAsInstalled.isEmpty()) {
                        markDependenciesAsInstalled(conn, dependenciesToMarkAsInstalled)
                        conn.getConnection()
                    }
                } catch (PSQLException e) {
                    project.logger.error("Error during Installation Status update: ${e.message}", e)
                } finally {
                    if (conn.getConnection() != null) {
                        conn.getConnection().close()
                    }
                }
            }
        }

        Iterable<String> taskNames = project.getGradle().getStartParameter().getTaskNames()
        if (taskNames.contains(UPDATE_DATABASE_TASK) || taskNames.contains(":" + UPDATE_DATABASE_TASK)) {
            List<String> modifiedTaskNames = new ArrayList<>(taskNames)
            int indexToAdd = modifiedTaskNames.indexOf(UPDATE_DATABASE_TASK) + 1
            modifiedTaskNames.add(indexToAdd, INSTALLATION_STATUS_UPDATE_TASK)

            project.getGradle().getStartParameter().setTaskNames(modifiedTaskNames)
        }
    }

    /**
     * Checks if the Dependency Manager module is installed in the project by
     * searching for it in the project sources and jars.
     *
     * @param project the project to check for Dependency Manager installation
     * @return true if the Dependency Manager is installed, false otherwise
     */
    static boolean depManagerIsInstalled(Project project) {
        // Check sources for Dependency Manager
        Project modules = project.findProject(Constants.MODULES_PROJECT)
        boolean depManagerInSrc = modules?.findProject(EtendoArtifactsConsistencyContainer.DEPENDENCY_MANAGER_PKG) != null

        // Check jars for Dependency Manager
        FileFilter fileFilter = { File file ->
            file.name == EtendoArtifactsConsistencyContainer.DEPENDENCY_MANAGER_PKG
        } as FileFilter

        File jarsDir = new File(project.buildDir.path, "etendo" + File.separator + Constants.MODULES_PROJECT)
        boolean depManagerInJars = jarsDir.listFiles(fileFilter)?.size() > 0

        return depManagerInSrc || depManagerInJars
    }

    /**
     * Retrieves a list of dependencies that need to be marked as installed
     * using the provided DatabaseConnection. Dependencies must match any of the following conditions
     * to be marked as 'Installed':
     * - There exists a module whose javapackage and version match the dependency's, and
     *   the dependency status is 'Pending Installation'
     * - The dependency is marked as external, and its status is 'Pending Installation'
     *
     * @param conn the DatabaseConnection object to execute the query
     * @return a list of dependency IDs to mark as installed
     */
    static List<String> getDependenciesToMarkAsInstalled(DatabaseConnection conn) {
        List<String> dependencyList = new ArrayList<>()
        String sqlQuery = """
            SELECT DEP.ETDEP_DEPENDENCY_ID FROM ETDEP_DEPENDENCY DEP
            JOIN AD_MODULE MOD ON (MOD.JAVAPACKAGE = CONCAT(DEP.DEPGROUP, '.', DEP.ARTIFACT)
                AND MOD.VERSION = DEP.VERSION)
            WHERE DEP.INSTALLATION_STATUS = 'PENDING'
            UNION
                (SELECT DEP.ETDEP_DEPENDENCY_ID FROM ETDEP_DEPENDENCY DEP
                WHERE DEP.ISEXTERNALDEPENDENCY = 'Y'
                AND DEP.INSTALLATION_STATUS = 'PENDING')
        """

        List<GroovyRowResult> queryResults = conn.executeSelectQuery(sqlQuery.toString())
        for (GroovyRowResult result : queryResults) {
            dependencyList.add(result.get("etdep_dependency_id") as String)
        }
        return dependencyList
    }

    /**
     * Marks the specified dependencies as installed in the database using the provided DatabaseConnection.
     *
     * @param conn the DatabaseConnection object to execute the update query
     * @param dependencyIds the list of dependency IDs to mark as installed
     */
    static void markDependenciesAsInstalled(DatabaseConnection conn, List<String> dependencyIds) {
        // Put a placeholder for a hardcoded param, as at least one is required in the 'update' method
        String sqlUpdate = """
        UPDATE ETDEP_DEPENDENCY SET INSTALLATION_STATUS = 'INSTALLED'
        WHERE ETDEP_DEPENDENCY_ID IN
            (${dependencyIds.collect { dependencyId -> "'${dependencyId}'" }.join(", ")})
            AND ISACTIVE = ?
        """

        conn.update(conn.getConnection(), sqlUpdate.toString(), ["Y"])
    }

}
