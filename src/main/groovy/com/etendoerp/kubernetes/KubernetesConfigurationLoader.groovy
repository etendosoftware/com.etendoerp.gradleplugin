package com.etendoerp.kubernetes

import org.gradle.api.Project
import org.gradle.api.Task

class KubernetesConfigurationLoader {
    static final String SMARTBUILD_TASK = "smartbuild"
    static final String DEPLOYK8_TASK   = "deployK8s"

    static void load(Project project) {
        Task deployK8sTask = project.tasks.findByName(DEPLOYK8_TASK)
        Task smartBuildTask = project.tasks.findByName(SMARTBUILD_TASK)

        if (smartBuildTask && deployK8sTask) {
            project.logger.info("* Adding '${DEPLOYK8_TASK}' configuration task to run after the '${smartBuildTask}'.")
            smartBuildTask.finalizedBy(deployK8sTask)
        }
    }
}
