package com.etendoerp.legacy.modules.expand

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.core.CoreMetadata
import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.legacy.dependencies.ArtifactDependency
import com.etendoerp.legacy.dependencies.ResolutionUtils
import com.etendoerp.legacy.dependencies.ResolverDependencyUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact

import java.lang.module.Configuration

class ExpandUtils {

    final static String SOURCE_MODULES_CONTAINER = "sourceModulesContainer"
    final static String EXPAND_SOURCES_RESOLUTION_CONTAINER = "expandSourcesResolutionContainer"

    static List<ArtifactDependency> getSourceModulesFiles(Project project, CoreMetadata coreMetadata) {
        def moduleDepConfig = project.configurations.getByName("moduleDeps")

        def extension = project.extensions.findByType(EtendoPluginExtension)
        def performResolutionConflicts = extension.performResolutionConflicts

        List<ArtifactDependency> artifactDependencies

        if (performResolutionConflicts) {
           artifactDependencies = performExpandResolutionConflicts(project, coreMetadata, true, false)
        }

        // TODO: use the 'artifactDependencies' (if not null) to obtain the correct version of the 'modulesDeps' dependencies

        project.logger.info("* Getting incoming dependencies from the '${moduleDepConfig.name}' configuration.")
        def incomingDependencies = ResolutionUtils.getIncomingDependencies(project, moduleDepConfig)

        return collectDependenciesFiles(project, incomingDependencies, "zip")
    }

    /**
     * Collect the defined extension of a dependency
     * Ex: 'com.test:mod:1.0.0@zip' collects the zip file of the dependency. '@zip' is the extension.
     *
     * @param dependencies The dependencies to collect
     * @return List<ArtifactDependency> List of ArtifactDependency objects
     */
    static List<ArtifactDependency> collectDependenciesFiles(Project project, List<ArtifactDependency> dependencies, String extension) {
        List<ArtifactDependency> collection = new ArrayList<>()

        int index = 0
        dependencies.each {
            def sourceDependency = "${it.displayName}@${extension}"
            try {
                project.logger.info("Trying to resolve the dependency: ${sourceDependency}")

                // Create a custom configuration container
                index++
                def configName = "${SOURCE_MODULES_CONTAINER}$index"
                def config = project.configurations.create(configName)

                // Add module dependency
                project.dependencies.add(configName, sourceDependency)

                def resolvedArtifact = config.resolvedConfiguration.resolvedArtifacts
                ArtifactDependency artifactDependency = null

                // The resolved artifact should be only one
                for (ResolvedArtifact artifact : resolvedArtifact) {
                    artifactDependency = new ArtifactDependency(project, artifact)
                }

                if (artifactDependency) {
                    project.logger.info("Dependency resolved: ${artifactDependency.resolvedArtifact.getId()}")
                    collection.add(artifactDependency)
                }

            } catch (Exception e) {
                project.logger.error("The dependency ${sourceDependency} could not be resolved.")
                project.logger.error(e.getMessage())
            }
        }
        return collection
    }

    static void performExpandResolutionConflicts(Project project, CoreMetadata coreMetadata, boolean addCoreDependency, boolean addProjectDependencies) {
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
        ResolutionUtils.dependenciesResolutionConflict(project, resolutionContainer)
    }

    // TODO: update the 'configurationToResolve' with the resolutionConfigurations
    static Configuration resolve(Project project, Configuration configurationToResolve, List<Configuration> resolutionConfigurations) {

    }


    static String getModuleName(String dependency) {
        def parts = dependency.split(":")
        def group = parts[0]
        def name = parts[1]
        return "${group}.${name}"
    }

}
