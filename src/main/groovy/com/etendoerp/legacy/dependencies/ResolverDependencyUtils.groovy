package com.etendoerp.legacy.dependencies

import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ResolvedArtifact

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

    /**
     * Loads the dependencies map with the module name has key and the Dependency has value.
     * @param container
     */
    static Map<String, Dependency> loadDependenciesMap(Project project, Configuration container) {
        Map<String, Dependency> dependenciesMap = new HashMap<>()
        for (Dependency dependency : container.dependencies) {
            def group = dependency.group
            def name = dependency.name
            String moduleName = "${group}.${name}"
            dependenciesMap.put(moduleName, dependency)
        }
        return dependenciesMap
    }

    /**
     * Filters the external dependencies by type:
     *  - MAVEN
     *  - ETENDOJARMODULE
     *  - ETENDOZIPMODULE
     *  - ETENDOCORE
     * @param project
     * @param containerToFilter The container with the dependencies to filter
     * @param dependencyContainer The dependency container to load with the filtered dependencies.
     */
    static void filterDependenciesFiles(Project project, Configuration containerToFilter, DependencyContainer dependencyContainer) {
        def dependenciesMap = loadDependenciesMap(project, containerToFilter)
        dependencyContainer.dependenciesMap = dependenciesMap
        def mavenDependenciesFiles = dependencyContainer.mavenDependenciesFiles
        def etendoDependenciesJarFiles = dependencyContainer.etendoDependenciesJarFiles
        def etendoDependenciesZipFiles = dependencyContainer.etendoDependenciesZipFiles

        def resolvedArtifacts = containerToFilter.resolvedConfiguration.resolvedArtifacts

        for (ResolvedArtifact resolvedArtifact : resolvedArtifacts) {
            ArtifactDependency artifactDependency = new ArtifactDependency(project, resolvedArtifact)
            // Get the 'Dependency' object
            artifactDependency.dependency = dependenciesMap.get(artifactDependency.moduleName)
            switch (artifactDependency.type) {
                case DependencyType.ETENDOCORE:
                    dependencyContainer.etendoCoreDependencyFile = artifactDependency
                    break
                case DependencyType.ETENDOJARMODULE:
                    etendoDependenciesJarFiles.put(artifactDependency.moduleName, artifactDependency)
                    break
                case DependencyType.ETENDOZIPMODULE:
                    etendoDependenciesZipFiles.put(artifactDependency.moduleName, artifactDependency)
                    break
                default:
                    mavenDependenciesFiles.put(artifactDependency.moduleName, artifactDependency)
                    break
            }
        }
    }

}
