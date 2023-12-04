package com.etendoerp.copilot.configuration

import com.etendoerp.copilot.Constants
import com.etendoerp.publication.PublicationUtils
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.plugins.ExtraPropertiesExtension

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

    static void load(Project project) {
        project.afterEvaluate {
            File copilotDir = new File(project.buildDir.path, COPILOT)
            copilotDir.deleteDir()

            boolean copilotExists = false
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
