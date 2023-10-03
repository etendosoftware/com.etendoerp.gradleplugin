package com.etendoerp.copilot.configuration


import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.configuration.pom.PomConfigurationContainer
import org.gradle.api.Project

class CopilotConfigurationLoader {

    final static String JAVA_SOURCES = "src"

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
        File copilotDir = new File("${project.buildDir.path}/copilot")
        copilotDir.deleteDir()

        Project moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}")
        if (moduleProject != null) {
            def copilotProject = moduleProject.findProject("com.etendoerp.copilot")
            if (copilotProject != null) {
                project.copy {
                    from {
                        copilotProject.projectDir.path
                    }
                    into "${project.buildDir}/copilot"
                    includeEmptyDirs false
                }
                moduleProject.subprojects.each {subproject ->
                    File toolsDir = new File(subproject.projectDir.path + "/tools")
                    if (toolsDir.exists()) {
                        project.copy {
                            from {
                                "${subproject.projectDir.path}/tools"
                            }
                            into "${project.buildDir}/copilot/tools"
                        }
                    }
                }
            }
        }
    }
}
