package com.etendoerp.copilot.dockerenv

import com.etendoerp.copilot.Constants
import com.etendoerp.copilot.exceptions.CopilotEnvironmentConfException
import org.gradle.api.Project

class CopilotEnvironmentVerification {

    private final static String ENVIRONMENT_ERROR_MESSAGE = "* The following mandatory environment variables have not been set: "
    private final static String COPILOT_MODULE_ABSENT = "* The Etendo Copilot module could not be found. Is it installed?"


    static void load(Project project) {
        project.tasks.register("copilotEnvironmentVerification") {
            doLast {
                project.logger.info("*****************************************************")
                project.logger.info("* Performing Copilot environment verification.")
                project.logger.info("*****************************************************")

                verifyParams(project)
                project.logger.info("* All environment variables OK.")
                verifyModuleIsInstalled(project)
                project.logger.info("* Module 'com.etendoerp.copilot' is installed.")
            }
        }
    }

    static void verifyParams(Project project) {
        boolean inconsistent = false
        List<String> notSetVars = new ArrayList<>()

        String errorMsg = "${ENVIRONMENT_ERROR_MESSAGE}"
        String openaiApiKey = project.ext.get(Constants.OPENAI_API_KEY_PROPERTY)
        String copilotPort = project.ext.get(Constants.COPILOT_PORT_PROPERTY)

        if (openaiApiKey.isEmpty())
            notSetVars.add(Constants.OPENAI_API_KEY_PROPERTY)
        if (copilotPort.isEmpty())
            notSetVars.add(Constants.COPILOT_PORT_PROPERTY)
        if (notSetVars.size() > 0)
            inconsistent = true

        if (inconsistent) {
            errorMsg += notSetVars.toString()
            throw new CopilotEnvironmentConfException(errorMsg)
        }
    }

    static void verifyModuleIsInstalled(Project project) {
        String errorMsg = "${COPILOT_MODULE_ABSENT} \n"
        // Check sources for copilot
        Project modules = project.findProject(Constants.MODULES_PROJECT)
        boolean copilotInSrc = modules.findProject(Constants.COPILOT_MODULE) != null

        // Check jars for copilot
        File jarsDir = new File(project.buildDir.path, "etendo" + File.separator + Constants.MODULES_PROJECT)
        boolean copilotInJars = jarsDir.listFiles().any {file ->
            file.name == Constants.COPILOT_MODULE
        }

        if (!copilotInSrc && !copilotInJars)
            throw new CopilotEnvironmentConfException(errorMsg)
    }
}
