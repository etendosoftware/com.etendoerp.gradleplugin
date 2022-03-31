package com.etendoerp.publication.taskloaders

import com.etendoerp.gradleutils.GradleUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project

// Class used to load the tasks need it to publish a subproject
class PublicationTaskLoader {

    static void load(Project mainProject, Project subProject) {

        if (!validateSubmodulePublicationTasks(mainProject, subProject)) {
            mainProject.logger.warn("* The publication tasks can not be loaded for the subproject '${subProject}'.")
            return
        }

        // Configure ZIP task
        ZipTaskGenerator.load(mainProject, subProject)

        // Configure JAR task
        JarTaskGenerator.load(mainProject, subProject)

        // Configure MAVEN publication task
        MavenTaskGenerator.load(mainProject, subProject)

        // Load the main publish task
        PublishTaskGenerator.load(mainProject, subProject)
    }

    static boolean validateSubmodulePublicationTasks(Project mainProject, Project subProject) {
        // Verify that the subproject contains the 'group' and 'artifact' properties
        def moduleNameOptional = PublicationUtils.loadModuleName(mainProject, subProject)

        if (moduleNameOptional.isEmpty()) {
            return false
        }

        // Verify that the subproject contains the MavenPublication
        def moduleCapitalize = PublicationUtils.capitalizeModule(moduleNameOptional.get())
        def publicationTaskName = "publish${moduleCapitalize}PublicationTo${PublishTaskGenerator.PUBLICATION_MAVEN_DESTINE}"
        def publicationTask = GradleUtils.getTaskByName(mainProject, subProject, publicationTaskName)

        if (!publicationTask) {
            mainProject.logger.warn("WARNING: The subproject ${subProject} is missing the maven publiction task '${publicationTaskName}'.")
            mainProject.logger.warn("*** Make sure that the 'build.gradle' file contains the MavenPublication '${publicationTaskName}'.")
            return false
        }
        return true
    }
}
