package com.etendoerp.copilot.tools

import com.etendoerp.copilot.Constants
import com.etendoerp.copilot.exceptions.CopilotEnvironmentConfException
import org.gradle.api.Project

class ToolsUtils {
    static final String MODULE_PKG_NOT_SET = "The module package has not been provided. Specify it with the '-P${Constants.ARG_PROPERTY}' property."
    static final String MODULE_PKG_NOT_EXISTS = "The '%s' module does not exist in sources."
    static final String COPILOT_NOT_FOUND = "Etendo Copilot was not found. Is it installed?"
    static final String TOOL_NOT_FOUND = "The '%s' tool was not found. Is it installed?"

    static void verifyModuleExistsInSources(Project project, String module) {
        project.logger.info("*****************************************************")
        project.logger.info("* Verifying module to translate exists in sources.")
        project.logger.info("*****************************************************")

        if (module == null || module.isEmpty()) {
            throw new IllegalArgumentException(MODULE_PKG_NOT_SET)
        }

        Project modulesProject = project.findProject("modules")
        if (modulesProject?.findProject(module) == null) {
            throw new IllegalArgumentException(String.format(MODULE_PKG_NOT_EXISTS, module))
        }
    }

    static void verifyCopilotAndToolAreInstalled(Project project, String tool) {
        project.logger.info("*****************************************************")
        project.logger.info("* Verifying copilot and given tool are installed.")
        project.logger.info("*****************************************************")

        File copilotDir = new File("${project.buildDir.path}/copilot")
        if (!copilotDir.exists()) {
            throw new CopilotEnvironmentConfException(COPILOT_NOT_FOUND)
        }
        File toolFile = new File("${copilotDir.path}/tools/${tool}")
        if (!toolFile.exists()) {
            throw new CopilotEnvironmentConfException(String.format(TOOL_NOT_FOUND, tool.replaceAll('\\..*', '')))
        }
    }
}
