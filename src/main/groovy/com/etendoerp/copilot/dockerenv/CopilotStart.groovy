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

                String openaiApiKey = System.getenv("OPENAI_API_KEY")
                String bastianUrl = System.getenv("BASTIAN_URL")
                String copilotPort = System.getenv("COPILOT_PORT")
                String dockerCommand = 'docker run -e OPENAI_API_KEY=' + "\"${openaiApiKey}\"" + ' -e BASTIAN_URL=' +
                        "\"${bastianUrl}\"" + ' -e COPILOT_PORT=' + "\"${copilotPort}\"" + ' -p ' + "${copilotPort}" +
                        ':5000 -v \$(pwd)/modules/com.etendoerp.copilot/:/app/ -v \$(pwd)/:/modules/ etendo/etendo_copilot_core:develop'

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
