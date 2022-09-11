package com.etendoerp.publication.taskloaders

import com.etendoerp.publication.configuration.pom.PomConfigurationContainer
import org.gradle.api.Project

class TaskLoaderUtils {

    static final String DUMMY_TASK_TEMPORARY_DIR = "DUMMY_TASK_TEMPORARY_DIR"

    static Optional<File> parseFilesToTemporaryDir(Project mainProject, Project subProject, File temporaryDir=null) {
        PomConfigurationContainer pomContainer = null

        if (subProject.ext.has(PomConfigurationContainer.POM_CONTAINER_PROPERTY)) {
            pomContainer = subProject.ext.get(PomConfigurationContainer.POM_CONTAINER_PROPERTY)
        }

        if (!pomContainer) {
            mainProject.logger.info("* The POM container is not defined for the '${subProject}'.")
            return Optional.empty()
        }

        if (!pomContainer.recursivePublication) {
            return Optional.empty()
        }

        if (!temporaryDir) {
            def dummyTask = subProject.tasks.findByName(DUMMY_TASK_TEMPORARY_DIR)
            def filesAlreadyParsed = subProject.findProperty(PomConfigurationContainer.PARSED_FILES_FLAG)

            // The task with the temporary dir containing the parsed files already exists
            if (dummyTask && filesAlreadyParsed) {
                return Optional.of(dummyTask.getTemporaryDir())
            }

            // Create dummy task
            if (!dummyTask) {
                dummyTask = subProject.tasks.register(DUMMY_TASK_TEMPORARY_DIR).get()
                // Clean the temporary dir
                subProject.delete(dummyTask.temporaryDir)
            }

            temporaryDir = dummyTask.getTemporaryDir()
        }

        // Parse
        return Optional.of(pomContainer.parseProjectFiles(temporaryDir))
    }

}
