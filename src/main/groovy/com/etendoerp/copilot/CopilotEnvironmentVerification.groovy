package com.etendoerp.copilot

import com.etendoerp.copilot.exceptions.CopilotEnvironmentConfException
import org.gradle.api.Project

class CopilotEnvironmentVerification {

    private final static String ENVIRONMENT_ERROR_MESSAGE = "* The following mandatory environment variables have not been set: "
    private final static String COPILOT_MODULE_ABSENT = "* The Etendo Copilot module could not be found. Is it installed?"

    private final static String OPENAI_API_KEY_PROPERTY = "OPENAI_API_KEY"
    private final static String COPILOT_PORT_PROPERTY = "COPILOT_PORT"

    private final static String MODULES_PROJECT = "modules"
    private final static String COPILOT_MODULE = "com.etendoerp.copilot"

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
        String openaiApiKey = project.ext.get('openaiAPIKey')
        String copilotPort = project.ext.get('copilotPort')

        if (openaiApiKey.isEmpty())
            notSetVars.add(OPENAI_API_KEY_PROPERTY)
        if (copilotPort.isEmpty())
            notSetVars.add(COPILOT_PORT_PROPERTY)
        if (notSetVars.size() > 0)
            inconsistent = true

        if (inconsistent) {
            errorMsg += notSetVars.toString()
            throw new CopilotEnvironmentConfException(errorMsg)
        }
    }

    static void verifyModuleIsInstalled(Project project) {
        String errorMsg = "${COPILOT_MODULE_ABSENT} \n"
        Project modules = project.findProject(MODULES_PROJECT)
        Project copilot = modules.findProject(COPILOT_MODULE)

        if (!copilot)
            throw new CopilotEnvironmentConfException(errorMsg)
    }
}
