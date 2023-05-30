package com.etendoerp.publication.taskloaders

import com.etendoerp.gradleutils.GradleUtils
import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet

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

        def subProjectConfigurations = DependencyUtils.loadListOfConfigurations(subProject, )
        def container = subProject.configurations.create(UUID.randomUUID().toString().replace("-",""))
        DependencySet containerSet = container.dependencies

        // Create a DependencySet with all the dependencies from the subproject
        DependencyUtils.loadDependenciesFromConfigurations(subProjectConfigurations, containerSet, true)


        // Verify that the subproject's core dependency top version has been changed from the default
        def regex = ", |,"
        def coreDependency = containerSet.find { (it.name == "etendo-core") }
        if (coreDependency != null) {
            def coreDependencyVersion = coreDependency.getVersion()
                    .replace("[", "")
                    .replace(")", "")
                    .split(regex) // Get the version range

            if ('x.y.z' == coreDependencyVersion.last()) {
                throw new IllegalArgumentException("The subproject ${subProject} core dependency top version must be different from 'x.y.z'.")
            }
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
