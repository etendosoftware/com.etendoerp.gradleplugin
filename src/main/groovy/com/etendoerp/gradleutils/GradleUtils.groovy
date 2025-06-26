package com.etendoerp.gradleutils

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

class GradleUtils {

    static TaskProvider<Task> getTaskByName(Project mainProject, Project subProject, String taskName) {
        TaskProvider<Task> task = null
        try {
            task = subProject.tasks.named(taskName)
        } catch (Exception e) {
            mainProject.logger.error("* Error trying to obtain the task '${taskName}' from '${subProject}'")
            mainProject.logger.error("* ERROR: ${e.getMessage()}")
        }
        return task
    }

    static <T> T loadProjectProperty(Project mainProject, Project subProject, ProjectProperty property, T value) {
        subProject.ext.set(property.toString(), value)
        return value
    }

    static Optional getProjectProperty(Project mainProject, Project subProject, ProjectProperty property) {
        if (subProject.ext.has(property.toString())) {
            return Optional.of(subProject.ext.get(property.toString()))
        }
        return Optional.empty()
    }

}
