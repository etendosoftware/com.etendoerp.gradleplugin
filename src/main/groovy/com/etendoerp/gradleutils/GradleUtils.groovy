package com.etendoerp.gradleutils

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

class GradleUtils {

    static setTasksOrder(Project project, List<String> tasks) {
        for (int i = 0; i < tasks.size() - 1; i++) {
            project.tasks.named(tasks.get(i + 1)).get().mustRunAfter(project.tasks.named(tasks.get(i)))
        }
    }

    /**
     * Receives a list of tasks and set the order to be ran.
     * The task should be ran following the order of the list
     * [task1, task2, task3]
     * order: task1 -> task2 -> task3
     * task3 mustRunAfter -> task2 mustRunAfter -> task1
     *
     * @param project
     * @param tasks
     */
    static setTaskOrder(Project project, List<Task> tasks) {
        for (int i = 0; i < tasks.size() - 1; i++) {
            tasks.get(i + 1).mustRunAfter(tasks.get(i))
        }
    }

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

    static void loadProjectProperty(Project mainProject, Project subProject, ProjectProperty property, Object value) {
        subProject.ext.set(property.toString(), value)
    }

    static Optional getProjectProperty(Project mainProject, Project subProject, ProjectProperty property) {
        def value = subProject.ext.get(property.toString())
        if (value != null) {
            return Optional.of(value)
        }
        return Optional.empty()
    }

}
