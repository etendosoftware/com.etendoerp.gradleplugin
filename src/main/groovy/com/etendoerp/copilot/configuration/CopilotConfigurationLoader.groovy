package com.etendoerp.copilot.configuration

import com.etendoerp.connections.DatabaseConnection
import com.etendoerp.copilot.Constants
import com.etendoerp.publication.PublicationUtils
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.sql.GroovyRowResult
import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension

import com.etendoerp.consistency.EtendoArtifactsConsistencyContainer

class CopilotConfigurationLoader {

    /**
     * This class makes al the necessary copilot configurations at the start of a build.
     *
     * The initialization of any gradle task will trigger the following:
     * <ul>
     *     <li> Generate a <i>build/copilot</i> directory, in which the following will be placed:
     *          <ul>
     *              <li> All of the <i>com.etendoerp.copilot</i> module's files
     *              <li> Files from any module related to copilot - this is, one of the copilot tools -. They will be placed in <i>build/copilot/tools</i>
     *          </ul>
     *     <li> Modify the copilot module's `tools_config.json`, adding dinamically the tools, like so:
     *         <pre>
     {
     "native_tools": {
     "BastianFetcher": true,
     },
     "third_party_tools": {
     "HelloWorldTool": true,
     "XMLTranslatorTool": true,
     ...
     }
     }
     *         </pre>
     * </ul>
     * @param project
     *
     */
    public static final String COPILOT = 'copilot'

    static Map<String, GroovyRowResult> getMapOfModules(Project prj, DatabaseConnection dbConnection) {
        Map<String, GroovyRowResult> map = new HashMap<>()
        if (!dbConnection) {
            return map
        }
        String qry = "select * from ad_module"
        def rowResult
        try {
            rowResult = dbConnection.executeSelectQuery(qry)
        } catch (Exception e) {
            prj.logger.info("* WARNING: The modules from the database could not be loaded to perform the version consistency verification.")
            prj.logger.info("* MESSAGE: ${e.message}")
        }
        if (rowResult) {
            for (GroovyRowResult row : rowResult) {
                def javaPackage = row.javapackage as String
                if (javaPackage) {
                    map.put(javaPackage, row)
                }
            }
        }
        return map
    }

    static void load(Project project) {
        project.afterEvaluate {
            //only do the copilot build if the task copilot.start is present
            if (!project.gradle.startParameter.taskNames.contains("copilot.start")) {
                return
            }
            def databaseConnection = new DatabaseConnection(project)
            def validConnection = databaseConnection.loadDatabaseConnection()
            Map<String, GroovyRowResult> modulesMap = getMapOfModules(project, databaseConnection)
            boolean copilotExists = false
            boolean doCopilotBuild = false
            int copilotVersionMajor = 1
            int copilotVersionMinor = 7
            String version = '0.0.0'
            if (modulesMap.containsKey("com.etendoerp.copilot")) {
                copilotExists = true
                version = modulesMap.get("com.etendoerp.copilot").get("version")
                copilotVersionMajor = version.split("\\.")[0].toInteger()
                copilotVersionMinor = version.split("\\.")[1].toInteger()
                if (copilotVersionMajor > 1 || (copilotVersionMajor == 1 && copilotVersionMinor >= 7)) {
                    doCopilotBuild = true
                }
            }
            if (!copilotExists || !doCopilotBuild) {
                return
            }
            if (copilotVersionMajor == 1 && copilotVersionMinor < 8) {
                project.logger.error("The version of Copilot is ${version}, please upgrade to 1.8.0 or higher," +
                        " because in that version Copilot is integrated with the Etendo Docker Management" +
                        " and this will be the supported way to run Copilot in next versions.")

            }
            File copilotDir = new File(project.buildDir.path, COPILOT)
            copilotDir.deleteDir()

            Project moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}")
            File jarModulesLocation = new File(project.buildDir, "etendo" + File.separator + Constants.MODULES_PROJECT)
            File copilotJarModule = new File(jarModulesLocation, Constants.COPILOT_MODULE)
            Project copilotProject = null
            if (moduleProject != null) {
                copilotProject = moduleProject.findProject(Constants.COPILOT_MODULE)
            }

            if (copilotProject != null) { // Copilot found in SOURCES
                copilotExists = true
                project.copy {
                    from {
                        copilotProject.projectDir.path
                    }
                    into "${project.buildDir.path}${File.separator}copilot"
                    includeEmptyDirs false
                }
            } else if (copilotJarModule.exists()) { // Copilot found in JARS
                copilotExists = true
                project.copy {
                    from {
                        copilotJarModule.path
                    }
                    into "${project.buildDir.path}${File.separator}copilot"
                    includeEmptyDirs false
                }
            }

            if (copilotExists) {
                File toolsConfigFile = new File(project.buildDir, COPILOT + File.separator + Constants.TOOLS_CONFIG_FILE)
                String toolDependencyFileName = getToolsDependenciesFileName(project)
                File toolsDependenciesFileMain = new File(project.buildDir, COPILOT + File.separator + toolDependencyFileName)
                def toolsConfigJson = new JsonSlurper().parseText(toolsConfigFile.readLines().join(" "))

                // Get tools in SOURCES
                if (moduleProject != null) {
                    moduleProject.subprojects.each { subproject ->
                        File toolsDir = new File(subproject.projectDir, "tools")
                        if (toolsDir.exists() && !subproject.name.equals(Constants.COPILOT_MODULE)) {
                            project.copy {
                                from {
                                    toolsDir.path
                                }
                                into "${project.buildDir.path}${File.separator}copilot${File.separator}tools"
                            }
                            toolsDir.listFiles().each { file ->
                                toolsConfigJson.third_party_tools[file.name.replaceFirst(~/\.[^\.]+$/, '')] = true
                            }
                            def json_data = JsonOutput.toJson(toolsConfigJson)
                            toolsConfigFile.write(JsonOutput.prettyPrint(json_data))
                            //lets read the Dependencies file of the subproject and add it to the main one
                            File toolsDependenciesFile = new File(subproject.projectDir, toolDependencyFileName)
                            if (toolsDependenciesFile.exists()) {
                                toolsDependenciesFileMain.append(toolsDependenciesFile.text)
                                project.logger.info("Added dependencies from ${subproject.name} to main dependencies file")
                            }
                        }
                    }
                }

                // Get tools in JARS
                jarModulesLocation.listFiles().each { jarModule ->
                    File jarModuleToolsDir = new File(jarModule, "tools")
                    if (jarModuleToolsDir.exists() && !jarModule.name.equals(Constants.COPILOT_MODULE)) {
                        project.copy {
                            from {
                                jarModuleToolsDir.path
                            }
                            into "${project.buildDir.path}${File.separator}copilot${File.separator}tools"
                        }
                        jarModuleToolsDir.listFiles().each { file ->
                            toolsConfigJson.third_party_tools[file.name.replaceFirst(~/\.[^\.]+$/, '')] = true
                        }
                        def json_data = JsonOutput.toJson(toolsConfigJson)
                        toolsConfigFile.write(JsonOutput.prettyPrint(json_data))
                        //lets read the Dependencies file of the subproject and add it to the main one
                        File toolsDependenciesFile = new File(jarModule, toolDependencyFileName)
                        if (toolsDependenciesFile.exists()) {
                            toolsDependenciesFileMain.append('\n')
                            toolsDependenciesFileMain.append(toolsDependenciesFile.text)
                            project.logger.info("Added dependencies from ${jarModule.name} to main dependencies file")
                        }
                    }
                }
            }
        }
    }

    private static String getToolsDependenciesFileName(Project project) {
        String toolsDependenciesFile = 'tools_deps.toml'
        try {
            String toolsDependenciesFileProp = project.ext.get(Constants.DEPENDENCIES_TOOLS_FILENAME)
            if (toolsDependenciesFileProp.isEmpty()) {
                return toolsDependenciesFile
            }
            return toolsDependenciesFileProp
        } catch (ExtraPropertiesExtension.UnknownPropertyException e) {
            project.logger.info("Failed to get TOOLS_DEPENDENCIES_FILE: ${e.getMessage()}. Default value 'tools_deps.toml' will be used.")
        }
        return toolsDependenciesFile
    }
}
