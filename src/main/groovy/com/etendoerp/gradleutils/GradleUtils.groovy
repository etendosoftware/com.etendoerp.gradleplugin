package com.etendoerp.gradleutils

import org.gradle.api.Project

class GradleUtils {

    static setTasksOrder(Project project, List<String> tasks) {
        for (int i = 0; i < tasks.size() - 1; i++) {
            project.tasks.named(tasks.get(i + 1)).get().mustRunAfter(project.tasks.named(tasks.get(i)))
        }
    }

}
