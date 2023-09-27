package com.etendoerp.copilot

import com.etendoerp.copilot.exceptions.CopilotEnvironmentConfException
import org.gradle.api.Project

class CopilotEnvironmentVerification {

    final static String COPILOT_START_TASK = "copilot.start"

    private final static String ENVIRONMENT_ERROR_MESSAGE = "* The following mandatory environment variables have not been set: "
    private final static String COPILOT_MODULE_ABSENT = "* The Etendo Copilot module could not be found. Is it installed?"

    private final static String OPENAI_API_KEY_ENV_VAR = "OPENAI_API_KEY"
    private final static String BASTIAN_URL_ENV_VAR = "BASTIAN_URL"
    private final static String COPILOT_PORT_ENV_VAR = "COPILOT_PORT"

    private final static String MODULES_PROJECT = "modules"
    private final static String COPILOT_MODULE = "com.etendoerp.copilot"

    static boolean skipCopilotEnv(List<String> gradleTasks) {
        if (!gradleTasks.contains(COPILOT_START_TASK) && !gradleTasks.contains(":${COPILOT_START_TASK}")) {
            return true
        }
        return false
    }

    static void load(Project project) {
        project.tasks.register("copilotEnvironmentVerification") {
            doLast {

                // Check if the 'copilot.start' task is being run
                // Identify the tasks being run
                def local = System.getProperty("local")
                if (skipCopilotEnv(project.gradle.startParameter.taskNames) || local == "no") {
                    project.logger.info("* Ignoring Copilot environment verification.")
                    return
                }

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
        String openaiApiKey = System.getenv(OPENAI_API_KEY_ENV_VAR)
        String bastianUrl = System.getenv(BASTIAN_URL_ENV_VAR)
        String copilotPort = System.getenv(COPILOT_PORT_ENV_VAR)

        if (!openaiApiKey || openaiApiKey.isEmpty())
            notSetVars.add(OPENAI_API_KEY_ENV_VAR)
        if (!bastianUrl || bastianUrl.isEmpty())
            notSetVars.add(BASTIAN_URL_ENV_VAR)
        if (!copilotPort || copilotPort.isEmpty())
            notSetVars.add(COPILOT_PORT_ENV_VAR)
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
