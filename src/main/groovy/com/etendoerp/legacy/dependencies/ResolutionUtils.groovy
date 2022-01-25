package com.etendoerp.legacy.dependencies

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.core.CoreMetadata
import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import com.etendoerp.legacy.dependencies.container.DependencyType
import com.etendoerp.publication.PublicationUtils
import groovy.io.FileType
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedDependencyResult
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.diagnostics.DependencyInsightReportTask
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector

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
     * @param filterCoreDependency Flag used to prevent adding the Core dependency to the returned Map
     */
    static Map<String, List<ArtifactDependency>> dependenciesResolutionConflict(Project project, Configuration configuration, boolean filterCoreDependency, boolean getSelected) {
        def extension = project.extensions.findByType(EtendoPluginExtension)

        def forceParameter = project.findProperty("force")
        def forcePluginExtension = extension.forceResolution

        def force = forceParameter || forcePluginExtension
        project.logger.info("")
        project.logger.info("* Performing the resolution conflicts of the configuration '${configuration.getName()}'.")

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
        return getIncomingDependencies(project, configuration, filterCoreDependency, getSelected, LogLevel.INFO, artifactsConflicts)
    }

    static void handleResolutionConflict(Project project, Configuration configuration, ComponentSelectionReasonInternal reason, ModuleVersionIdentifier module, boolean force) {
        project.logger.info("")
        project.logger.info("********************************************")
        project.logger.info("* Found a conflict resolution with: ${module}")
        project.logger.info("* Description: ${reason.descriptions}")
        def group = module.group
        def name = module.name

        String taskReportName = RESOLUTION_REPORT_TASK + UUID.randomUUID().toString().replace("-","")

        // Create task to report the dependency graph
        def reportTask = project.tasks.register(taskReportName, DependencyInsightReportTask).get()
        reportTask.setConfiguration(configuration)
        reportTask.setDependencySpec("${group}:${name}")
        project.logger.info("****************** REPORT ******************")
        project.logger.info("")

        def logLevel = project.gradle.startParameter.logLevel.name()
        if (logLevel == "INFO" || logLevel == "DEBUG") {
            reportTask.report()
        }

        // Throw on core conflict
        if (isCoreDependency(module.toString()) && !force) {
            throw new IllegalArgumentException("Cannot have a conflict with the core dependency - ${module}")
        }
    }

    /**
     * Get the 'requested' or 'selected' incoming dependencies.
     * The 'requested' dependencies are those defined by the user.
     * The 'selected' dependencies are those resolved by gradle.
     * Ex: requested: 'com.test:mymod:[1.0.0, 1.0.3]' -> selected: 'com.test:mymod:1.0.2'
     * @param project
     * @param configuration
     * @param filterCoreDependency Flag used to prevent adding the Core dependency to the returned Map
     * @param artifactConflicts Map used to add to the ArtifactDependency the 'hasConflict' flag.
     * @return
     */
    static Map<String, List<ArtifactDependency>> getIncomingDependencies(Project project, Configuration configuration, boolean filterCoreDependency, boolean getSelected, LogLevel logLevel, Map < String, Boolean > artifactConflicts = null) {
        Map<String, List<ArtifactDependency>> incomingDependencies = [:]
        configuration.incoming.each {
            for (DependencyResult dependency: it.resolutionResult.allDependencies) {
                if (dependency instanceof  DefaultUnresolvedDependencyResult) {
                    DefaultUnresolvedDependencyResult unresolved = dependency as DefaultUnresolvedDependencyResult
                    project.logger.error("*************************************************")
                    project.logger.error("The requested dependency '${unresolved.requested.displayName}' could not be resolved.")
                    project.logger.error("Attempted reason: ${unresolved.attemptedReason}")
                    project.logger.error("Failure: ${unresolved.failure}")
                    project.logger.error("*************************************************")
                    continue
                }

                if (dependency instanceof DefaultResolvedDependencyResult) {
                    DefaultResolvedDependencyResult dependencyResult = dependency as DefaultResolvedDependencyResult

                    ArtifactDependency artifactDependency = null
                    String artifactName = ""

                    if (getSelected) {
                        ModuleVersionIdentifier identifier = dependencyResult.getSelected().moduleVersion
                        String displayName = dependencyResult.getSelected().getId().displayName
                        artifactDependency = new ArtifactDependency(project, identifier, displayName)
                        artifactName = "${identifier.group}:${identifier.name}"
                    } else {
                        def requested = dependencyResult.getRequested()
                        if (requested instanceof DefaultModuleComponentSelector) {
                            requested = requested as DefaultModuleComponentSelector
                            artifactDependency = new ArtifactDependency(project, requested.displayName)
                            artifactName = "${artifactDependency.group}:${artifactDependency.name}"
                        }

                    }

                    // Check if the artifact has conflicts
                    if (artifactConflicts && artifactConflicts.containsKey(artifactName)) {
                        artifactDependency.hasConflicts = artifactConflicts.get(artifactName)
                    }

                    String displayName = artifactDependency.displayName

                    project.logger.log(logLevel, "Requested dependency: ${dependencyResult.getRequested()} -> Selected: ${dependencyResult.getSelected()}")
                    if (filterCoreDependency && isCoreDependency(displayName)) {
                        continue
                    }

                    List<ArtifactDependency> artifactList = incomingDependencies.get(artifactName)
                    if (!artifactList) {
                        artifactList = new ArrayList<>()
                    }
                    artifactList.add(artifactDependency)
                    incomingDependencies.put(artifactName, artifactList)
                }

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

        String configName = SOURCE_MODULES_RESOLUTION + UUID.randomUUID().toString().replace("-","")
        def sourcesModulesResolution = project.configurations.create(configName)

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
            String configName = SOURCE_MODULES_CONTAINER + UUID.randomUUID().toString().replace("-","")
            sourcesModulesContainer = project.configurations.create(configName)
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

    static Map<String, List<ArtifactDependency>> performResolutionConflicts(Project project, Configuration configToPerformResolution, boolean filterCoreDependency, boolean getSelected) {

        // Obtain all the incoming 'requested' dependencies (Core dependencies will be not included)
        def requestedDependencies = getIncomingDependenciesExcludingCore(project, configToPerformResolution, filterCoreDependency, false)

        // Create a new configuration container (using the 'configuration' passed has parameter to restore the Core dependency)
        def configurationContainer = ResolverDependencyUtils.createRandomConfiguration(project,"resolution", configToPerformResolution)

        // Add all the requested dependencies to the new container
        ResolverDependencyUtils.loadConfigurationWithArtifacts(project, configurationContainer, requestedDependencies)

        // Perform the resolution conflicts
        return dependenciesResolutionConflict(project, configurationContainer, filterCoreDependency, getSelected)
    }

    /**
     * Obtain all the incoming dependencies from a Configuration (included transitives ones),
     * but exclude those dependencies that the core depends on
     * @param project
     * @param configuration
     * @param filterCoreDependency
     * @param getSelected
     * @return
     */
    static Map<String, List<ArtifactDependency>> getIncomingDependenciesExcludingCore(Project project, Configuration configuration, boolean filterCoreDependency, boolean getSelected) {
        // Create a random configuration to exclude Core transitive dependencies (The ones defined in the core pom.xml)
        def randomContainer = ResolverDependencyUtils.createRandomConfiguration(project, "incoming", configuration)
        ResolverDependencyUtils.excludeCoreDependencies(project, randomContainer, false)

        // Obtain all the incoming dependencies (Core dependencies will be not included)
        return getIncomingDependencies(project, randomContainer, filterCoreDependency, getSelected, LogLevel.DEBUG)
    }


}
