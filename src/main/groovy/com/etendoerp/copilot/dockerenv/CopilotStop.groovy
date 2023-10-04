package com.etendoerp.copilot.dockerenv;

import org.gradle.api.Project;

class CopilotStop {
    static void load(Project project) {
        project.tasks.register("copilot.stop") {
            dependsOn({ project.tasks.named("copilotEnvironmentVerification") })
            doLast {
                String copilotPort = project.ext.get("copilotPort")
                project.exec {
                    workingDir '.'
                    commandLine 'sh', '-c', 'docker stop $(docker ps -qf '+"\"publish=${copilotPort}\""+' -f "ancestor=etendo/etendo_copilot_core:develop")'
                }
            }
        }
    }
}
