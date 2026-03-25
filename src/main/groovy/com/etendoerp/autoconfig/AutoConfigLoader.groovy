package com.etendoerp.autoconfig

import org.gradle.api.Project

class AutoConfigLoader {
    static void load(Project project) {
        project.tasks.register("setup.autoConfig", AutoConfigTask) {
            group = "Etendo Auto-Config"
            description = "Runs auto-configuration tasks for registered modules (requires Tomcat)."
        }
    }
}
