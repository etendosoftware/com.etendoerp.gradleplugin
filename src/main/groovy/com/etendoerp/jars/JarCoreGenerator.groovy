package com.etendoerp.jars

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.bundling.Jar


class JarCoreGenerator {
    static load(Project project) {

        project.tasks.register("jarConfig") {
            doLast {
                project.logger.info("Starting JAR configuration.")
                def jarTask = (project.jar as Jar)
                def generated = Utils.loadGeneratedEntitiesFile(project)
                jarTask.archiveBaseName.set('etendo-core')
                //Excluding src-gen
                jarTask.exclude(PathUtils.fromPackageToPathClass(generated))
            }
        }
        project.jar.dependsOn("jarConfig")
    }
}
