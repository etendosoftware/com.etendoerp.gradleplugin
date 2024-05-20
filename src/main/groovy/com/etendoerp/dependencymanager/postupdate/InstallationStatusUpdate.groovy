package com.etendoerp.dependencymanager.postupdate

import com.etendoerp.connections.DatabaseConnection
import com.etendoerp.consistency.EtendoArtifactsConsistencyContainer
import com.etendoerp.copilot.Constants
import groovy.sql.GroovyRowResult
import org.gradle.api.Project

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
                String infoMsg = ""
                infoMsg += "\n\n************* DEPENDENCIES INSTALLATION STATUS UPDATE ************* \n"
                infoMsg += "* The Dependency Manager module is installed in the system.\n"
                infoMsg += "* Performing Installation Status Update.\n"
                infoMsg += "*******************************************************************\n\n"
                project.logger.info(infoMsg)

                DatabaseConnection conn = new DatabaseConnection(project)
                conn.loadDatabaseConnection()
                List<String> dependenciesToMarkAsInstalled = getDependenciesToMarkAsInstalled(conn)
                if (!dependenciesToMarkAsInstalled.isEmpty()) {
                    markDependenciesAsInstalled(conn, dependenciesToMarkAsInstalled)
                    conn.getConnection()
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


    static boolean depManagerIsInstalled(Project project) {
        // Check sources for Dependency Manager
        Project modules = project.findProject(Constants.MODULES_PROJECT)
        boolean depManagerInSrc = modules?.findProject(EtendoArtifactsConsistencyContainer.DEPENDENCY_MANAGER_PKG) != null

        // Check jars for Dependency Manager
        FileFilter fileFilter = new FileFilter() {
            @Override
            boolean accept(File file) {
                return file.name == EtendoArtifactsConsistencyContainer.DEPENDENCY_MANAGER_PKG
            }
        }

        File jarsDir = new File(project.buildDir.path, "etendo" + File.separator + Constants.MODULES_PROJECT)
        boolean depManagerInJars = jarsDir.listFiles(fileFilter)?.size() > 0

        return depManagerInSrc || depManagerInJars
    }

    static List<String> getDependenciesToMarkAsInstalled(DatabaseConnection conn) {
        List<String> dependencyList = new ArrayList<>()
        StringBuilder sqlQuery = new StringBuilder()
        sqlQuery.append("SELECT DEP.ETDEP_DEPENDENCY_ID FROM ETDEP_DEPENDENCY DEP ")
        sqlQuery.append("JOIN AD_MODULE MOD ON (MOD.JAVAPACKAGE = CONCAT(DEP.DEPGROUP, '.', DEP.ARTIFACT) ")
        sqlQuery.append("    AND MOD.VERSION = DEP.VERSION) ")
        sqlQuery.append("WHERE DEP.INSTALLATION_STATUS = 'PENDING' ")
        sqlQuery.append("UNION ")
        sqlQuery.append("    (SELECT DEP.ETDEP_DEPENDENCY_ID FROM ETDEP_DEPENDENCY DEP ")
        sqlQuery.append("    WHERE DEP.ISEXTERNALDEPENDENCY = 'Y' ")
        sqlQuery.append("    AND DEP.INSTALLATION_STATUS = 'PENDING')")

        List<GroovyRowResult> queryResults = conn.executeSelectQuery(sqlQuery.toString())
        for (GroovyRowResult result : queryResults) {
            dependencyList.add(result.get("etdep_dependency_id") as String)
        }
        return dependencyList
    }

    static void markDependenciesAsInstalled(DatabaseConnection conn, List<String> dependencyIds) {
        String idsString = dependencyIds.collect { "'${it}'" }.join(", ")
        StringBuilder sqlUpdate = new StringBuilder()
        sqlUpdate.append("UPDATE ETDEP_DEPENDENCY SET INSTALLATION_STATUS = 'INSTALLED' ")
        // Put a placeholder for a hardcoded param, as at least one is required in the 'update' method
        sqlUpdate.append("WHERE ETDEP_DEPENDENCY_ID IN (${idsString}) AND ISACTIVE = ?")
        conn.update(conn.getConnection(), sqlUpdate.toString(), ["Y"])
    }
}
