package com.etendoerp.legacy.modules.expand

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.core.CoreMetadata
import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.legacy.dependencies.ArtifactDependency
import com.etendoerp.legacy.dependencies.ResolutionUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact

class ExpandUtils {

    final static String SOURCE_MODULES_CONTAINER = "sourceModulesContainer"
    final static String EXPAND_SOURCES_RESOLUTION_CONTAINER = "expandSourcesResolutionContainer"

    static List<ArtifactDependency> getSourceModulesFiles(Project project, CoreMetadata coreMetadata) {
        def moduleDepConfig = project.configurations.getByName("moduleDeps")

        def extension = project.extensions.findByType(EtendoPluginExtension)
        def performResolutionConflicts = extension.performResolutionConflicts

        if (performResolutionConflicts) {
            // Create custom configuration container
            def resolutionContainer = project.configurations.create(EXPAND_SOURCES_RESOLUTION_CONTAINER)
            def resolutionDependencySet = resolutionContainer.dependencies

            // Add the current core version
            def core = "${coreMetadata.coreGroup}:${coreMetadata.coreName}:${coreMetadata.coreVersion}"
            project.logger.info("* Adding the core dependency to perform resolution conflicts. ${core}")
            project.dependencies.add(EXPAND_SOURCES_RESOLUTION_CONTAINER, core)

            // Load source modules dependencies to perform resolution.
            def sourceDepConfig = ResolutionUtils.loadSourceModulesDependenciesResolution(project)

            // Add the defined 'moduleDeps' and source dependencies to the resolution container
            DependencyUtils.loadDependenciesFromConfigurations([moduleDepConfig, sourceDepConfig], resolutionDependencySet)

            ResolutionUtils.dependenciesResolutionConflict(project, resolutionContainer)
        }

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
    static List<ArtifactDependency> collectDependenciesFiles(Project project, List<String> dependencies, String extension) {
        List<ArtifactDependency> collection = new ArrayList<>()

        int index = 0
        dependencies.each {
            def sourceDependency = "${it}@${extension}"
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

    static String getModuleName(String dependency) {
        def parts = dependency.split(":")
        def group = parts[0]
        def name = parts[1]
        return "${group}.${name}"
    }

}
