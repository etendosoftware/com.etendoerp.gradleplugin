package com.etendoerp.copilot.dockerenv

import com.etendoerp.copilot.Constants
import org.gradle.api.Project

class CopilotStart {

    static void load(Project project) {

        project.tasks.register("copilot.start") {
            dependsOn({ project.tasks.named("copilotEnvironmentVerification") })
            doLast {
                project.logger.info("*****************************************************")
                project.logger.info("* Performing copilot start task.")
                project.logger.info("*****************************************************")

                String copilotPort = project.ext.get(Constants.COPILOT_PORT_PROPERTY)

                String dockerCommand = 'docker run --env-file=\$(pwd)/gradle.properties -p ' + "${copilotPort}" + ':' + "${copilotPort}" +
                            ' -v ' + "${project.buildDir.path}/copilot/:/app/ " +
                            '-v ' + "\$(pwd)/modules:/modules/ etendo/${Constants.COPILOT_DOCKER_REPO}:develop"

                project.exec {
                    commandLine 'sh', '-c', 'docker pull etendo/' + "${Constants.COPILOT_DOCKER_REPO}:develop"
                }
                project.exec {
                    commandLine 'sh', '-c', dockerCommand
                }
            }
        }
    }
}
