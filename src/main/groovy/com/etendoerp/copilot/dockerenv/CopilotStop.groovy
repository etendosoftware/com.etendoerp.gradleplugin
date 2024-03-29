package com.etendoerp.copilot.dockerenv

import com.etendoerp.copilot.Constants
import org.gradle.api.Project

class CopilotStop {
    static void load(Project project) {
        project.tasks.register("copilot.stop") {
            dependsOn({ project.tasks.named("copilotEnvironmentVerification") })
            doLast {
                String copilotPort = project.ext.get(Constants.COPILOT_PORT_PROPERTY)
                String copilotTag = CopilotStart.getCopilotImageTag(project)
                project.exec {
                    workingDir '.'
                    commandLine 'sh', '-c', 'docker stop $(docker ps -qf ' + "\"publish=${copilotPort}\"" +
                            ' -f "ancestor=etendo/' + "${Constants.COPILOT_DOCKER_REPO}:${copilotTag}\")"
                }
            }
        }
    }
}
