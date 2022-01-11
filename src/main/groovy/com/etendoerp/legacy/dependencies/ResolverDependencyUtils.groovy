package com.etendoerp.legacy.dependencies

import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet

class ResolverDependencyUtils {

    /**
     * Loads all the dependencies of the project and subproject in a custom Configuration
     * @param project
     * @return Configuration
     */
    static Configuration loadAllDependencies(Project project) {
        // Get all the base project configurations
        def baseProjectConfigurations = DependencyUtils.loadListOfConfigurations(project)

        // Get all the subproject configurations
        def subprojectConfigurations = DependencyUtils.getConfigurationsFromProject(project)

        // Add all the subproject configurations to the base project configuration
        baseProjectConfigurations.addAll(subprojectConfigurations)

        // The configuration container allows to filter equals dependencies with different versions
        // The container will contain the last version of a dependency
        def container = project.configurations.findByName(PublicationUtils.ETENDO_DEPENDENCY_CONTAINER)

        DependencySet containerSet = container.dependencies

        // Create a DependencySet with all the dependency from the base project and subprojects
        // The DependencySet can contain same dependencies with different versions
        DependencyUtils.loadDependenciesFromConfigurations(baseProjectConfigurations, containerSet)

        return container
    }

}
