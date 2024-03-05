package com.etendoerp.externaltasks

import org.gradle.api.Project

class ExternalTasksLoader {
    static void load(Project project) {
        AddTasks.load(project)
    }
}
