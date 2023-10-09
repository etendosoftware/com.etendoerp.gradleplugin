package com.etendoerp.copilot.configuration

import com.etendoerp.publication.PublicationUtils
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Project

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
     */
    static void load(Project project) {
        project.afterEvaluate {
            File copilotDir = new File("${project.buildDir.path}/copilot")
            copilotDir.deleteDir()

            Project moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}")
            if (moduleProject != null) {
                Project copilotProject = moduleProject.findProject("com.etendoerp.copilot")
                File jarModulesLocation = new File(project.buildDir, "etendo" + File.separator + "modules")
                File copilotJarModule = new File(jarModulesLocation, "com.etendoerp.copilot")

                boolean copilotExists = false
                if (copilotProject != null) { // Copilot found in SOURCES
                    copilotExists = true
                    project.copy {
                        from {
                            copilotProject.projectDir.path
                        }
                        into "${project.buildDir.path}/copilot"
                        includeEmptyDirs false
                    }
                } else if (copilotJarModule.exists()) { // Copilot found in JARS
                    copilotExists = true
                    project.copy {
                        from {
                            copilotJarModule.path
                        }
                        into "${project.buildDir.path}/copilot"
                        includeEmptyDirs false
                    }
                }

                if (copilotExists) {
                    File toolsConfigFile = new File(project.buildDir, "copilot" + File.separator + "tools_config.json")
                    def toolsConfigJson = new JsonSlurper().parseText(toolsConfigFile.readLines().join(" "))

                    // Get tools in SOURCES
                    moduleProject.subprojects.each { subproject ->
                        File toolsDir = new File(subproject.projectDir, "tools")
                        if (toolsDir.exists() && !subproject.name.equals("com.etendoerp.copilot")) {
                            project.copy {
                                from {
                                    toolsDir.path
                                }
                                into "${project.buildDir.path}/copilot/tools"
                            }
                            toolsDir.listFiles().each { file ->
                                toolsConfigJson.third_party_tools[file.name.replaceFirst(~/\.[^\.]+$/, '')] = true
                            }
                            def json_data = JsonOutput.toJson(toolsConfigJson)
                            toolsConfigFile.write(JsonOutput.prettyPrint(json_data))
                        }
                    }

                    // Get tools in JARS
                    jarModulesLocation.listFiles().each { jarModule ->
                        File jarModuleToolsDir = new File(jarModule, "tools")
                        if (jarModuleToolsDir.exists() && !jarModule.name.equals("com.etendoerp.copilot")) {
                            project.copy {
                                from {
                                    jarModuleToolsDir.path
                                }
                                into "${project.buildDir.path}/copilot/tools"
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
    }
}
