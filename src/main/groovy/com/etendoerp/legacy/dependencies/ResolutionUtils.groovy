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
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedDependencyResult
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.diagnostics.DependencyInsightReportTask
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector

/**
 * Class containing helper methods to perform resolution version conflicts.
 */
class ResolutionUtils {

    final static String SOURCE_MODULES_CONTAINER  = "sourcesModulesContainer"
    final static String SOURCE_MODULES_RESOLUTION = "sourceModulesResolution"

    final static String RESOLUTION_REPORT_TASK = "resolutionReportTask"

    final static String CORE_CONFLICTS_ERROR_MESSAGE = "Cannot have a conflict with the core dependency "
    final static String CONFLICT_WARNING_MESSAGE     = "Found a conflict resolution with:"

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
     * @param obtainSelectedArtifacts Flag used to obtain only the 'selected' artifacts resolved by gradle if true, otherwise obtains the requested by the user.
     */
    static Map<String, List<ArtifactDependency>> dependenciesResolutionConflict(Project project, Configuration configuration, boolean filterCoreDependency, boolean obtainSelectedArtifacts,
                                                                                LogLevel logLevel=LogLevel.INFO, List<String> modulesToReport=[], List<String> modulesToNotReport=[]) {
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
                    handleResolutionConflict(project, configuration, reason, module, force, modulesToReport, modulesToNotReport)
                } else {
                    artifactsConflicts.put(artifactName, false)
                }
            }
        }
        // Trigger the resolution
        return getIncomingDependencies(project, configuration, filterCoreDependency, obtainSelectedArtifacts, logLevel, artifactsConflicts)
    }

    static void handleResolutionConflict(Project project, Configuration configuration, ComponentSelectionReasonInternal reason, ModuleVersionIdentifier module, boolean force,
                                         List<String> modulesToReport=[], List<String> modulesToNotReport=[]) {
        def isCoreDependency = isCoreDependency(module.toString())
        def group = module.group
        def name = module.name
        def moduleIdentifier = "${group}:${name}".toLowerCase()

        boolean shouldReport = (!(moduleIdentifier in modulesToNotReport*.toLowerCase())
                && (modulesToReport.isEmpty() || moduleIdentifier in modulesToReport*.toLowerCase()))

        String taskReportName = RESOLUTION_REPORT_TASK + UUID.randomUUID().toString().replace("-","")
        if (shouldReport) {
            project.logger.info("")
            project.logger.info("********************************************")
            project.logger.info("* ${CONFLICT_WARNING_MESSAGE} ${module}")
            project.logger.info("* Description: ${reason.descriptions}")

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
        }

        // Throw on core conflict
        if (isCoreDependency && !force) {
            def errorMessage = "${CORE_CONFLICTS_ERROR_MESSAGE} - ${module} \n"
            errorMessage += EtendoPluginExtension.forceResolutionMessage()
            throw new IllegalArgumentException(errorMessage)
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
     * @param obtainSelectedArtifacts Flag used to obtain only the 'selected' artifacts resolved by gradle if true, otherwise obtains the requested by the user.
     * @param artifactConflicts Map used to add to the ArtifactDependency the 'hasConflict' flag.
     * @return
     */
    static Map<String, List<ArtifactDependency>> getIncomingDependencies(Project project, Configuration configuration, boolean filterCoreDependency, boolean obtainSelectedArtifacts, LogLevel logLevel, Map < String, Boolean > artifactConflicts = null) {
        Map<String, List<ArtifactDependency>> incomingDependencies = [:]
        configuration.incoming.each {
            for (DependencyResult dependency: it.resolutionResult.allDependencies) {
                if (dependency instanceof  DefaultUnresolvedDependencyResult) {
                    DefaultUnresolvedDependencyResult unresolved = dependency as DefaultUnresolvedDependencyResult
                    project.logger.info("********************* ERROR *********************")
                    project.logger.info("The requested dependency '${unresolved.requested.displayName}' could not be resolved.")
                    project.logger.info("Attempted reason: ${unresolved.attemptedReason}")
                    project.logger.info("Failure: ${unresolved.failure}")
                    project.logger.info("*************************************************")
                    continue
                }

                if (dependency instanceof DefaultResolvedDependencyResult) {
                    DefaultResolvedDependencyResult dependencyResult = dependency as DefaultResolvedDependencyResult

                    ArtifactDependency artifactDependency = null
                    String artifactName = ""

                    if (obtainSelectedArtifacts) {
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

                    if (artifactDependency != null) {
                        artifactDependency.dependencyResult = dependencyResult
                        artifactDependency.artifactName = artifactName

                        String conflicts = ""

                        // Check if the artifact has conflicts
                        if (artifactConflicts && artifactConflicts.containsKey(artifactName)) {
                            artifactDependency.hasConflicts = artifactConflicts.get(artifactName)
                            conflicts = "- Conflicts: ${artifactDependency.hasConflicts}"
                        }

                        String displayName = artifactDependency.displayName

                        if (filterCoreDependency && isCoreDependency(displayName)) {
                            continue
                        }

                        project.logger.log(logLevel, "Requested dependency: ${dependencyResult.getRequested()} -> Selected: ${dependencyResult.getSelected()} ${conflicts}")

                        List<ArtifactDependency> artifactList = incomingDependencies.get(artifactName)
                        if (!artifactList) {
                            artifactList = new ArrayList<>()
                        }
                        artifactList.add(artifactDependency)
                        incomingDependencies.put(artifactName, artifactList)
                    }
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

        def modulesLocation = new File(project.rootDir, PublicationUtils.BASE_MODULE_DIR)

        if (!modulesLocation.exists()) {
            return sourcesModulesContainer
        }
        
        project.logger.info("Loading source modules dependencies from 'modules/'.")

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

    /**
     * Performs the resolution of conflicts aiming the CORE dependency.
     * The resolution has to be done in multiple steps because each pom dependency is excluding the CORE.
     *
     * To perform the resolution the dependencies has to be at the same 'level'
     *
     * @param project
     * @param configToPerformResolution
     * @return
     */
    static Map<String, List<ArtifactDependency>> performCoreResolutionConflicts(Project project, Configuration configToPerformResolution) {
        // Obtain all the incoming 'requested' dependencies (Core dependencies will be not included)
        def requestedDependencies = getIncomingDependenciesExcludingCore(project, configToPerformResolution, false, false)

        // Create a new configuration container (using the 'configuration' passed has parameter to restore the Core dependency)
        def configurationContainer = ResolverDependencyUtils.createRandomConfiguration(project,"core-resolution", configToPerformResolution)

        // Add all the requested dependencies to the new container
        // All the dependencies will be at the same 'level'
        ResolverDependencyUtils.loadConfigurationWithArtifacts(project, configurationContainer, requestedDependencies,
                false, true, true, true)

        // Perform the resolution conflicts
        return dependenciesResolutionConflict(project, configurationContainer, false,
                true, LogLevel.DEBUG, CORE_DEPENDENCIES)
    }

    static Map<String, List<ArtifactDependency>> performResolutionConflicts(Project project, Configuration configToPerformResolution, boolean filterCoreDependency, boolean obtainSelectedArtifacts) {
        def coreResolutionDependencies = performCoreResolutionConflicts(project, configToPerformResolution)

        ArtifactDependency coreArtifactDependency = null
        String currentCoreDependency = null

        // Filter the resolved core
        CoreMetadata coreMetadata = project.findProperty(CoreMetadata.CORE_METADATA_PROPERTY) as CoreMetadata

        if (coreMetadata) {
            currentCoreDependency = "${coreMetadata.coreGroup}:${coreMetadata.coreName}"
            coreArtifactDependency = ResolverDependencyUtils.getCoreDependency(project, currentCoreDependency, coreResolutionDependencies)

            // Update the CORE version
            if (coreArtifactDependency != null && !coreArtifactDependency.hasConflicts) {
                Set<DefaultExternalModuleDependency> dependencies = ResolverDependencyUtils.filterDependenciesByName(project, configToPerformResolution, coreArtifactDependency.group, coreArtifactDependency.name)
                ResolverDependencyUtils.updateDependenciesVersion(project, dependencies, coreArtifactDependency.version)
            }
        }

        // Perform the resolution conflicts
        def resolvedArtifacts = dependenciesResolutionConflict(project, configToPerformResolution, filterCoreDependency,
                obtainSelectedArtifacts, LogLevel.INFO, [], CORE_DEPENDENCIES)

        // Add to the result the selected CORE
        if (currentCoreDependency && coreArtifactDependency && !filterCoreDependency) {
            resolvedArtifacts.put(currentCoreDependency, [coreArtifactDependency])
        }

        return resolvedArtifacts
    }

    /**
     * Obtain all the incoming dependencies from a Configuration (included transitives ones),
     * but exclude those dependencies that the core depends on
     * @param project
     * @param configuration
     * @param filterCoreDependency
     * @param obtainSelectedArtifacts Flag used to obtain only the 'selected' artifacts resolved by gradle if true, otherwise obtains the requested by the user.
     * @return
     */
    static Map<String, List<ArtifactDependency>> getIncomingDependenciesExcludingCore(Project project, Configuration configuration, boolean filterCoreDependency, boolean obtainSelectedArtifacts) {
        // Create a random configuration to exclude Core transitive dependencies (The ones defined in the core pom.xml)
        def randomContainer = ResolverDependencyUtils.createRandomConfiguration(project, "incoming", configuration)
        ResolverDependencyUtils.excludeCoreDependencies(project, randomContainer, false)

        // Obtain all the incoming dependencies (Core dependencies will be not included)
        return getIncomingDependencies(project, randomContainer, filterCoreDependency, obtainSelectedArtifacts, LogLevel.DEBUG)
    }


}
