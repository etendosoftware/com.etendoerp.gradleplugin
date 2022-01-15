package com.etendoerp.legacy.dependencies

import com.etendoerp.core.CoreMetadata
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
        def container = project.configurations.create(UUID.randomUUID().toString().replace("-",""))

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

    /**
     * Creates a custom Configuration loaded with the ArtifactDependencies and Configurations passed has parameter.
     * @param project
     * @param configurations
     * @param artifactDependencyMap
     * @return
     */
    static Configuration createConfigurationFromArtifacts(Project project, List<Configuration> configurations, Map<String, ArtifactDependency> artifactDependencyMap) {

        def configurationContainer = project.configurations.create(UUID.randomUUID().toString().replace("-",""))
        def configurationDependencySet = configurationContainer.dependencies

        // Load the configurations
        DependencyUtils.loadDependenciesFromConfigurations(configurations, configurationDependencySet)

        // Load the Artifact dependencies
        for (def entry : artifactDependencyMap.entrySet()) {
            String displayName = entry.value.displayName
            if (displayName) {
                project.dependencies.add(configurationContainer.name, displayName)
            }
        }

        return configurationContainer
    }

    /**
     * Obtains the incoming dependencies from a Configuration.
     * Updates the dependencies from the Configuration with the passed 'artifactDependencyMap'.
     *
     * This is used to prevent adding extra Dependencies to a Configuration,
     * when the configuration should only contain defined user Dependencies.
     *
     * Ex: The 'moduleDeps' config is used to define dependencies to expand, the dependencies are defined by the user.
     * The incoming dependencies from the 'moduleDeps' are those defined by the user and the transitives ones.
     * If a user wants to obtain the correct version of a defined Dependency taking into account Etendo modules already installed,
     * a resolution conflict should be performed, the result should be the correct versions.
     *
     * The 'artifactDependencyMap' contains the resolution version result of the dependencies ('moduleDeps', Defined source Etendo Modules, etc.).
     *
     * @param project
     * @param configuration
     * @param artifactDependencyMap
     * @param filterCoreDependency
     * @param updateOnConflicts Prevent updating the dependency if has conflicts (Left the defined user version)
     * @return
     */
    static Configuration updateConfigurationDependencies(Project project, Configuration configuration, Map<String, ArtifactDependency> artifactDependencyMap, boolean filterCoreDependency, boolean updateOnConflicts) {

        // Obtain the incoming dependencies from the Configuration
        project.logger.info("* Getting incoming dependencies from the configuration '${configuration.name}' to be updated.")
        def incomingDependencies = ResolutionUtils.getIncomingDependencies(project, configuration, filterCoreDependency)

        // Update the dependencies
        for (def entry : artifactDependencyMap.entrySet()) {
            if (entry.value.hasConflicts && !updateOnConflicts) {
                continue
            }

            if (incomingDependencies.containsKey(entry.key)) {
                incomingDependencies.put(entry.key, entry.value)
            }
        }

       return createConfigurationFromArtifacts(project, [configuration], incomingDependencies)
    }

    static void excludeDependencies(Project project, Configuration configuration, List<ArtifactDependency> dependenciesToExclude) {
        for (ArtifactDependency dependency : dependenciesToExclude) {
            String group = dependency.group
            String name  = dependency.name
            String artifactName = "${group}:${name}"

            configuration.dependencies.removeIf({
                String dependencyName = "${it.group}:${it.name}"
                return dependencyName.contains(artifactName)
            })

            // Exclude transitives dependencies
            configuration.exclude([group : "$group", module: "$name"])
        }
    }

    static void excludeCoreDependencies(Project project, Configuration configuration) {
        ArtifactDependency defaultCore = new ArtifactDependency(project, CoreMetadata.DEFAULT_ETENDO_CORE_GROUP, CoreMetadata.DEFAULT_ETENDO_CORE_NAME, "1.0.0")
        ArtifactDependency classicCore = new ArtifactDependency(project, CoreMetadata.CLASSIC_ETENDO_CORE_GROUP, CoreMetadata.CLASSIC_ETENDO_CORE_NAME, "1.0.0")
        excludeDependencies(project, configuration, [defaultCore, classicCore])
    }

    /**
     * Creates a random Configuration.
     * If the 'configurationToAdd' is passed has a parameter, loads all the dependencies to the new configuration.
     * @param project
     * @param configurationToAdd
     * @return
     */
    static Configuration createRandomConfiguration(Project project, Configuration configurationToAdd = null) {
        def config = project.configurations.create(UUID.randomUUID().toString().replace("-",""))
        if (configurationToAdd) {
            DependencyUtils.loadDependenciesFromConfigurations([configurationToAdd], config.dependencies)
        }
        return config
    }

}
