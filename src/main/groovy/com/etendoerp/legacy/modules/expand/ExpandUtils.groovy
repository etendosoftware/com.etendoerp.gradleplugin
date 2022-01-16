package com.etendoerp.legacy.modules.expand

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.core.CoreMetadata
import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.legacy.dependencies.ResolutionUtils
import com.etendoerp.legacy.dependencies.ResolverDependencyUtils
import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import com.etendoerp.legacy.dependencies.container.DependencyContainer
import com.etendoerp.legacy.dependencies.container.DependencyType
import com.etendoerp.publication.PublicationUtils
import groovy.io.FileType
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact

class ExpandUtils {

    final static String SOURCE_MODULES_CONTAINER = "sourceModulesContainer"
    final static String EXPAND_SOURCES_RESOLUTION_CONTAINER = "expandSourcesResolutionContainer"

    static List<ArtifactDependency> getSourceModulesFiles(Project project, Configuration configuration, CoreMetadata coreMetadata) {
        def configurationToExpand = ResolverDependencyUtils.createRandomConfiguration(project, configuration)

        def extension = project.extensions.findByType(EtendoPluginExtension)
        def performResolutionConflicts = extension.performResolutionConflicts

        def supportJars = coreMetadata.supportJars

        if (performResolutionConflicts) {
           def artifactDependencies = performExpandResolutionConflicts(project, coreMetadata, true, supportJars)
           configurationToExpand = ResolverDependencyUtils.updateConfigurationDependencies(project, configurationToExpand, artifactDependencies, true, false)
        }

        // Filter core dependencies
        ResolverDependencyUtils.excludeCoreDependencies(project, configurationToExpand)

        if (supportJars) {
            DependencyContainer dependencyContainer = new DependencyContainer(project, coreMetadata)
            dependencyContainer.configuration = configurationToExpand
            dependencyContainer.filterDependenciesFiles()
            return dependencyContainer.etendoDependenciesZipFiles.collect {it.value}
        } else {
            project.logger.info("* Getting incoming dependencies from the '${configurationToExpand.name}' configuration.")
            def incomingDependencies = ResolutionUtils.getIncomingDependencies(project, configurationToExpand, true)
            return collectDependenciesFiles(project, incomingDependencies, "zip", true)
        }

    }


    /**
     * * Collect the defined extension of a dependency
     * Ex: 'com.test:mod:1.0.0@zip' collects the zip file of the dependency. '@zip' is the extension
     *
     * @param project
     * @param dependencies
     * @param extension
     * @param ignoreCore Flag used to prevent downloading the Core when is already in sources.
     * @return
     */
    static List<ArtifactDependency> collectDependenciesFiles(Project project, Map<String, ArtifactDependency> dependencies, String extension, boolean ignoreCore) {
        List<ArtifactDependency> collection = new ArrayList<>()

        for (def entry : dependencies.entrySet()) {
            String displayName = entry.value.displayName

            if (ignoreCore && ResolutionUtils.isCoreDependency(displayName)) {
                continue
            }

            def artifact = collectArtifactDependencyFile(project, displayName, extension)
            if (artifact) {
                collection.add(artifact)
            }
        }
        return collection
    }

    static ArtifactDependency collectArtifactDependencyFile(Project project, String displayName, String extension) {
        String dependency = "${displayName}@${extension}"
        ArtifactDependency artifactDependency = null
        try {
            project.logger.info("* Trying to resolve the dependency: ${dependency}")

            // Create a custom configuration container
            def configurationContainer = project.configurations.create(UUID.randomUUID().toString().replace("-",""))

            // Add module dependency
            project.dependencies.add(configurationContainer.name, dependency)

            // Resolve artifact
            def resolvedArtifact = configurationContainer.resolvedConfiguration.resolvedArtifacts

            // The resolved artifact should be only one
            for (ResolvedArtifact artifact : resolvedArtifact) {
                artifactDependency = DependencyContainer.getArtifactDependency(project, artifact)//new ArtifactDependency(project, artifact)
            }

            if (artifactDependency) {
                project.logger.info("Dependency resolved: ${artifactDependency.resolvedArtifact.getId()}")
            }

        } catch (Exception e) {
            project.logger.error("The dependency ${dependency} could not be resolved.")
            project.logger.error(e.getMessage())
        }

        return artifactDependency
    }

    static Map<String , ArtifactDependency> performExpandResolutionConflicts(Project project, CoreMetadata coreMetadata, boolean addCoreDependency, boolean addProjectDependencies) {
        // Create custom configuration container
        def resolutionContainer = project.configurations.create(EXPAND_SOURCES_RESOLUTION_CONTAINER)
        def resolutionDependencySet = resolutionContainer.dependencies

        if (addCoreDependency) {
            // Add the current core version
            def core = "${coreMetadata.coreGroup}:${coreMetadata.coreName}:${coreMetadata.coreVersion}"
            project.logger.info("* Adding the core dependency to perform resolution conflicts. ${core}")
            project.dependencies.add(EXPAND_SOURCES_RESOLUTION_CONTAINER, core)
        }

        def configurationsToLoad = []

        // Load user defined dependencies
        def moduleDepConfig = project.configurations.getByName("moduleDeps")
        configurationsToLoad.add(moduleDepConfig)

        // Load source modules dependencies to perform resolution.
        def sourceDepConfig = ResolutionUtils.loadSourceModulesDependenciesResolution(project)
        configurationsToLoad.add(sourceDepConfig)

        if (addProjectDependencies) {
            // Load project dependencies
            def projectDependencies = ResolverDependencyUtils.loadAllDependencies(project)
            configurationsToLoad.add(projectDependencies)
        }

        // Add the defined dependencies to the resolution container
        DependencyUtils.loadDependenciesFromConfigurations(configurationsToLoad, resolutionDependencySet)

        // Perform resolution
        return ResolutionUtils.dependenciesResolutionConflict(project, resolutionContainer, true)
    }

    static String getModuleName(String dependency) {
        def parts = dependency.split(":")
        def group = parts[0]
        def name = parts[1]
        return "${group}.${name}"
    }

    /**
     * Extracts the modules in ZIP format
     * @param project
     * @param coreMetadata
     * @param artifactDependencies
     */
    static void expandModulesOnlySources(Project project, CoreMetadata coreMetadata, Configuration configuration, List<ArtifactDependency> artifactDependencies) {
        def extension = project.extensions.findByType(EtendoPluginExtension)
        def overwrite = extension.overwriteTransitiveExpandModules

        def dependencyMap = ResolverDependencyUtils.loadDependenciesMap(project, configuration)
        def sourceModulesMap = getSourceModules(project)

        for (ArtifactDependency artifact in artifactDependencies) {

            if (artifact.type != DependencyType.ETENDOZIPMODULE) {
                continue
            }

            String moduleName = artifact.moduleName
            // Prevent extracting transitive modules if the overwrite flag is set to false,
            // the module is already in sources
            // and the module was not defined by the user
            if (!overwrite && sourceModulesMap.containsKey(moduleName) && !dependencyMap.containsKey(moduleName)) {
                continue
            }

            artifact.extract()
        }
    }

    static Map<String, File> getSourceModules(Project project) {
        Map<String, File> sourceModules = new HashMap<>()
        def modulesLocation = new File(project.rootDir, PublicationUtils.BASE_MODULE_DIR)
        // Add the source modules
        modulesLocation.traverse(type: FileType.DIRECTORIES, maxDepth: 0) {
            sourceModules.put(it.name, it)
        }
        return sourceModules
    }

}
