package com.etendoerp.plugin;

import org.gradle.api.Project;

class EtendoModule {

    static void load(Project project) {

        project.tasks.register("plugintest") {
            doLast {
                project.logger.info("Etendo plugin test")
            }
        }

    }
}
