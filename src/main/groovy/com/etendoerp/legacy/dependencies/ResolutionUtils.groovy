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
import org.gradle.api.tasks.diagnostics.DependencyInsightReportTask

/**
 * Class containing helper methods to perform resolution version conflicts.
 */
class ResolutionUtils {

    final static String SOURCE_MODULES_CONTAINER  = "sourcesModulesContainer"
    final static String SOURCE_MODULES_RESOLUTION = "sourceModulesResolution"

    final static String RESOLUTION_REPORT_TASK = "resolutionReportTask"

    static List<String> CORE_DEPENDENCIES = [
            "${CoreMetadata.CLASSIC_ETENDO_CORE_GROUP}:${CoreMetadata.CLASSIC_ETENDO_CORE_NAME}",
            "${CoreMetadata.DEFAULT_ETENDO_CORE_GROUP}:${CoreMetadata.DEFAULT_ETENDO_CORE_NAME}"
    ]

    /**
     * Obtains the incoming dependencies and performs the resolution versions conflicts.
     * Throws a Exception if the dependency is the core and the 'force' flag is set to false.
     * @param project
     * @param configuration
     * @param ignoreCore Flag used to prevent adding the Core dependency to the returned Map
     */
    static Map<String, ArtifactDependency> dependenciesResolutionConflict(Project project, Configuration configuration, boolean ignoreCore) {
        def extension = project.extensions.findByType(EtendoPluginExtension)

        def forceParameter = project.findProperty("force")
        def forcePluginExtension = extension.forceResolution

        def force = forceParameter || forcePluginExtension

        project.logger.info("Performing the resolution conflicts of the configuration '${configuration.getName()}'.")

        Map<String, Boolean> artifactsConflicts = new HashMap<>()

        configuration.incoming.afterResolve {
            resolutionResult.allComponents {
                ComponentSelectionReasonInternal reason = selectionReason
                ModuleVersionIdentifier module = moduleVersion
                String artifactName = "${module.group}:${module.name}"
                artifactsConflicts.put(artifactName, true)
                if (reason.conflictResolution && module != null) {
                    artifactsConflicts.put(artifactName, true)
                    handleResolutionConflict(project, configuration, reason, module, force)
                } else {
                    artifactsConflicts.put(artifactName, false)
                }
            }
        }
        // Trigger the resolution
        return getIncomingDependencies(project, configuration, ignoreCore, artifactsConflicts)
    }

    static void handleResolutionConflict(Project project, Configuration configuration, ComponentSelectionReasonInternal reason, ModuleVersionIdentifier module, boolean force) {
        project.logger.info("********************************************")
        project.logger.info("Found a conflict resolution with: ${module}")
        project.logger.info("Description: ${reason.descriptions}")
        def group = module.group
        def name = module.name

        // Create task to report the dependency graph
        def reportTask = project.tasks.register("${RESOLUTION_REPORT_TASK}${System.currentTimeMillis()}", DependencyInsightReportTask).get()
        reportTask.setConfiguration(configuration)
        reportTask.setDependencySpec("${group}:${name}")
        project.logger.info("****************** REPORT ******************")
        reportTask.report()

        // Throw on core conflict
        if (isCoreDependency(module.toString()) && !force) {
            throw new IllegalArgumentException("Cannot have a conflict with the core dependency - ${module}")
        }
    }

    /**
     * Get the 'selected' incoming dependencies.
     * The 'requested' dependencies are those defined by the user.
     * The 'selected' dependencies are those resolved by gradle.
     * Ex: requested: 'com.test:mymod:[1.0.0, 1.0.3]' -> selected: 'com.test:mymod:1.0.2'
     * @param project
     * @param configuration
     * @param ignoreCore Flag used to prevent adding the Core dependency to the returned Map
     * @param artifactConflicts Map used to add to the ArtifactDependency the 'hasConflict' flag.
     * @return
     */
    static Map<String, ArtifactDependency> getIncomingDependencies(Project project, Configuration configuration, boolean ignoreCore, Map<String, Boolean> artifactConflicts = null) {
        Map<String, ArtifactDependency> incomingDependencies = [:]
        configuration.incoming.each {
            for (DependencyResult dependency: it.resolutionResult.allDependencies) {
                DefaultResolvedDependencyResult dependencyResult = dependency as DefaultResolvedDependencyResult
                ModuleVersionIdentifier identifier = dependencyResult.getSelected().moduleVersion
                String displayName = dependencyResult.getSelected().getId().displayName
                ArtifactDependency artifactDependency = new ArtifactDependency(project, identifier, displayName)
                String artifactName = "${identifier.group}:${identifier.name}"

                // Check if the artifact has conflicts
                if (artifactConflicts && artifactConflicts.containsKey(artifactName)) {
                    artifactDependency.hasConflicts = artifactConflicts.get(artifactName)
                }

                project.logger.info("Requested dependency: ${dependencyResult.getRequested()} -> Selected: ${dependencyResult.getSelected()}")
                if (ignoreCore && isCoreDependency(displayName)) {
                    continue
                }
                incomingDependencies.put(artifactName, artifactDependency)
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

    static Configuration loadSourceModulesDependenciesResolution(Project project) {
        def extension = project.extensions.findByType(EtendoPluginExtension)
        def sourcesModulesResolution = project.configurations.create(SOURCE_MODULES_RESOLUTION + System.currentTimeMillis())

        if (extension.ignoreSourceModulesResolution) {
            project.logger.info("Ignoring source modules resolution.")
            return sourcesModulesResolution
        }

        project.logger.info("Loading sources modules dependencies to perform resolution.")
        return loadSourceModulesDependencies(project, sourcesModulesResolution)
    }

    /**
     * Loads all the source modules dependencies to the configuration passed, or creates a new one.
     * The obtained source modules are those containing the 'etendo.artifact.metadata' file.
     * @param project
     * @param configuration
     * @return
     */
    static Configuration loadSourceModulesDependencies(Project project, Configuration configuration = null) {

        def sourcesModulesContainer = configuration

        // Creates a configuration container
        if (!sourcesModulesContainer) {
            sourcesModulesContainer = project.configurations.create(SOURCE_MODULES_CONTAINER + System.currentTimeMillis())
        }

        project.logger.info("Loading source modules dependencies from 'modules/'.")

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
                project.logger.info("Source module dependency loaded: ${sourceModule}")
                project.dependencies.add(sourcesModulesContainer.name, sourceModule)
            }
        }
        return sourcesModulesContainer
    }

}
