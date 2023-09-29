package com.etendoerp.copilot.dockerenv

import org.gradle.api.Project

class CopilotStart {

    static void load(Project project) {

        project.tasks.register("copilot.start") {
            dependsOn({ project.tasks.named("copilotEnvironmentVerification") })
            doLast {
                project.logger.info("*****************************************************")
                project.logger.info("* Performing copilot start task.")
                project.logger.info("*****************************************************")

                String openaiApiKey = project.ext.get('openaiAPIKey')
                String copilotPort = project.ext.get('copilotPort')
                String bastianUrl = null
                try {
                    bastianUrl = project.ext.get('bastianURL')
                } catch (ignored) {}

                String dockerEnvVars = 'docker run -e OPENAI_API_KEY=' + "\"${openaiApiKey}\"" + ' -e COPILOT_PORT=' + "\"${copilotPort}\""
                if (bastianUrl)
                    dockerEnvVars += ' -e BASTIAN_URL=' + "\"${bastianUrl}\""
                String dockerCommand = dockerEnvVars + ' -p ' + "${copilotPort}" + ':' + "${copilotPort}" +
                        ' -v \$(pwd)/modules/com.etendoerp.copilot/:/app/ ' +
                        '-v \$(pwd)/modules:/modules/ etendo/etendo_copilot_core:develop'

                project.exec {
                    workingDir '.'
                    commandLine 'sh', '-c', 'docker pull etendo/etendo_copilot_core:develop'
                }
                project.exec {
                    workingDir '.'
                    commandLine 'sh', '-c', dockerCommand
                }
            }
        }
    }
}
