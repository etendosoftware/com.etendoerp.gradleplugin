package com.etendoerp.legacy.dependencies

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.core.CoreMetadata
import com.etendoerp.publication.PublicationUtils
import groovy.io.FileType
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult

/**
 * Class containing helper methods to perform resolution version conflicts.
 */
class ResolutionUtils {

    final static String SOURCE_MODULES_CONTAINER = "sourcesModulesContainer"

    static List<String> CORE_DEPENDENCIES = [
            "${CoreMetadata.CLASSIC_ETENDO_CORE_GROUP}:${CoreMetadata.CLASSIC_ETENDO_CORE_NAME}",
            "${CoreMetadata.DEFAULT_ETENDO_CORE_GROUP}:${CoreMetadata.DEFAULT_ETENDO_CORE_NAME}"
    ]

    /**
     * Obtains the incoming dependencies and performs the resolution versions conflicts
     * Throws a Exception if the dependency is the core and the 'force' flag is set to false.
     * @param project
     * @param configuration
     */
    static List<String> dependenciesResolutionConflict(Project project, Configuration configuration) {
        def configurationName = configuration.getName()
        def extension = project.extensions.findByType(EtendoPluginExtension)

        def forceParameter = project.findProperty("force")
        def forcePluginExtension = extension.forceResolution

        def force = forceParameter || forcePluginExtension

        project.logger.info("Performing the resolution conflicts of the configuration '${configuration.getName()}'.")

        configuration.incoming.afterResolve {
            resolutionResult.allComponents {
                ComponentSelectionReasonInternal reason = selectionReason
                ModuleVersionIdentifier module = moduleVersion
                if (reason.conflictResolution && module != null) {
                    project.logger.info("********************************************")
                    project.logger.info("Found a conflict resolution with: ${module}")
                    project.logger.info("Description: ${reason.descriptions}")
                    def group = module.group
                    def name = module.name

                    project.logger.info("To obtain more information run: ./gradlew :dependencyInsight --configuration ${configurationName} --dependency ${group}:${name}")
                    project.logger.info("********************************************")

                    // Throw on core conflict
                    if (isCoreDependency(module.toString()) && !force) {
                        throw new IllegalArgumentException("Cannot have a conflict with the core dependency - ${module}")
                    }
                }
            }
        }
        // Trigger the resolution
        return getIncomingDependencies(project, configuration)
    }

    /**
     * Get the 'selected' incoming dependencies.
     * The 'requested' dependencies are those defined by the user.
     * The 'selected' dependencies are those resolved by gradle.
     * Ex: requested: 'com.test:mymod:[1.0.0, 1.0.3]' -> selected: 'com.test:mymod:1.0.2'
     * @param project
     * @param configuration
     * @return
     */
    static List<String> getIncomingDependencies(Project project, Configuration configuration) {
        List<String> incomingDependencies = []
        configuration.incoming.each {
            for (DependencyResult dependency: it.resolutionResult.allDependencies) {
                DefaultResolvedDependencyResult dependencyResult = dependency as DefaultResolvedDependencyResult
                def dependencyName = dependencyResult.getSelected().getId().toString()
                project.logger.error("Requested dependency: ${dependencyResult.getRequested()} -> Selected: ${dependencyResult.getSelected()}")
                if (isCoreDependency(dependencyName)) {
                    continue
                }
                incomingDependencies.add(dependencyName)
            }
        }
        return incomingDependencies
    }

    static boolean isCoreDependency(String dependency) {
        for (String coreDependency : CORE_DEPENDENCIES) {
            if (dependency.contains(coreDependency)) {
                return true
            }
        }
        return false
    }

    /**
     * Creates a custom configuration and loads all the Source modules dependencies
     * containing the 'etendo.artifact.metadata' file.
     * @param project
     * @return
     */
    static Configuration loadSourceModulesDependencies(Project project) {
        def extension = project.extensions.findByType(EtendoPluginExtension)

        // Creates a configuration container
        def sourcesModulesContainer = project.configurations.create(SOURCE_MODULES_CONTAINER + System.currentTimeMillis())

        if (extension.ignoreSourceModulesResolution) {
            project.logger.error("Ignoring source modules resolution.")
            return sourcesModulesContainer
        }

        project.logger.error("Loading source modules dependencies from 'modules/' to perform the resolution conflicts.")

        def modulesLocation = new File(project.rootDir, PublicationUtils.BASE_MODULE_DIR)
        List<File> sourceModules = new ArrayList<>()
        // Add the source modules
        modulesLocation.traverse(type: FileType.DIRECTORIES, maxDepth: 0) {
            sourceModules.add(it)
        }

        for (File sourceModuleLocation : sourceModules) {
            EtendoArtifactMetadata moduleMetadata = new EtendoArtifactMetadata(project, DependencyType.ETENDOZIPMODULE)

            if (moduleMetadata.loadMetadataFile(sourceModuleLocation.absolutePath)) {
                def group = moduleMetadata.group
                def name = moduleMetadata.name
                def version = moduleMetadata.version
                def sourceModule = "${group}:${name}:${version}"
                project.dependencies.add(sourcesModulesContainer.name, sourceModule)
            }
        }
        return sourcesModulesContainer
    }

}
