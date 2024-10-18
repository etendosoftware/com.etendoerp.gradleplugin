package com.etendoerp.legacy.dependencies

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.core.CoreMetadata
import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import com.etendoerp.legacy.dependencies.container.DependencyType
import com.etendoerp.modules.ModulesConfigurationUtils
import com.etendoerp.publication.PublicationUtils
import groovy.io.FileType
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
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
    public static final String MODULES_PROJECT = "modules"
    public static final String DEPENDENCY_MANAGER_PKG = "com.etendoerp.dependencymanager"

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

        // Throw on core conflict, unless Dependency Manager module is installed
        if (isCoreDependency && !(force || depManagerModuleInstalled(project))) {
            def errorMessage = "${CORE_CONFLICTS_ERROR_MESSAGE} - ${module} \n"
            errorMessage += EtendoPluginExtension.forceResolutionMessage()
            throw new IllegalArgumentException(errorMessage)
        }
    }

    /**
     * Checks if the Dependency Manager module is installed in the specified project.
     * This method determines if the module is installed by checking two potential locations:
     * (1) within the project structure under the 'modules' directory (Sources),
     * (2) as a jar file in a predefined modules directory within the project's build directory (Jar).
     *
     * @param project The project in which to check for the installation of the Dependency Manager module.
     * @return {@code true} if the Dependency Manager module is either found as a project module or exists as
     * a jar file in the specified location; {@code false} otherwise.
     */
    private static boolean depManagerModuleInstalled(Project project) {
        Project moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}")
        File jarModulesLocation = new File(project.buildDir, "etendo" + File.separator + MODULES_PROJECT)
        File depManagerJarModule = new File(jarModulesLocation, DEPENDENCY_MANAGER_PKG)
        Project depManagerProject = null
        if (moduleProject != null) {
            depManagerProject = moduleProject.findProject(DEPENDENCY_MANAGER_PKG)
        }
        return depManagerProject != null || depManagerJarModule.exists()
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
                    project.logger.log(logLevel, "********************* ERROR *********************")
                    project.logger.log(logLevel,"The requested dependency '${unresolved.requested.displayName}' could not be resolved.")
                    project.logger.log(logLevel,"Attempted reason: ${unresolved.attemptedReason}")
                    project.logger.log(logLevel,"Failure: ${unresolved.failure}")
                    project.logger.log(logLevel,"*************************************************")
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
                        if (dependencyResult.getSelected().getId() instanceof DefaultProjectComponentIdentifier) {
                            DefaultProjectComponentIdentifier projectIdentifier = dependencyResult.getSelected().getId() as DefaultProjectComponentIdentifier
                            artifactDependency.isProjectDependency = true

                            def subprojectNameOpt = ModulesConfigurationUtils.getSubprojectNameFromPath(project, projectIdentifier.getProjectPath())
                            if (subprojectNameOpt.isPresent()) {
                                artifactName = subprojectNameOpt.get()
                            }
                        }
                    } else {
                        def requested = dependencyResult.getRequested()
                        if (requested instanceof DefaultModuleComponentSelector) {
                            requested = requested as DefaultModuleComponentSelector
                            artifactDependency = new ArtifactDependency(project, requested.displayName)
                            artifactName = "${artifactDependency.group}:${artifactDependency.name}"
                        } else if (requested instanceof DefaultProjectComponentSelector) {
                            def requestedProject = requested as DefaultProjectComponentSelector
                            artifactDependency = new ArtifactDependency(project)
                            artifactDependency.isProjectDependency = true

                            def subprojectNameOpt = ModulesConfigurationUtils.getSubprojectNameFromPath(project, requestedProject.getProjectPath())
                            if (subprojectNameOpt.isPresent()) {
                                artifactName = subprojectNameOpt.get()
                            }
                        }
                    }

                    if (artifactDependency && !artifactName.isBlank()) {
                        artifactDependency.dependencyResult = dependencyResult
                        artifactDependency.artifactName = artifactName

                        String conflicts = ""

                        // Check if the artifact has conflicts
                        if (artifactConflicts && artifactConflicts.containsKey(artifactName)) {
                            artifactDependency.hasConflicts = artifactConflicts.get(artifactName)
                            conflicts = "- Conflicts: ${artifactDependency.hasConflicts}"
                        }

                        String displayName = artifactDependency.displayName
                        if (filterCoreDependency && displayName != null && isCoreDependency(displayName)) {
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
    static Map<String, List<ArtifactDependency>> performCoreResolutionConflicts(Project project, Configuration configToPerformResolution, boolean removeSourceModules) {
        // Obtain all the incoming 'requested' dependencies (Core dependencies will be not included)
        def configCopy = configToPerformResolution.copyRecursive()
        ResolverDependencyUtils.excludeCoreDependencies(project, configCopy, false)
        def requestedDependencies = getIncomingDependencies(project, configCopy, true, false, LogLevel.DEBUG)

        if (removeSourceModules) {
            // Filter the dependencies already in sources
            def subprojectNames = ModulesConfigurationUtils.getSubprojectNames(project)
            requestedDependencies.removeAll {
                subprojectNames.containsKey(it.key)
            }
        }

        // Create a new configuration container (using the 'configuration' passed has parameter to restore the Core dependency)
        def configurationContainer = ResolverDependencyUtils.createExtendedConfiguration(
                project,
                "core-resolution",
                configToPerformResolution).copyRecursive()

        // Add all the requested dependencies to the new container
        // All the dependencies will be at the same 'level'
        ResolverDependencyUtils.loadConfigurationWithArtifacts(project, configurationContainer, requestedDependencies,
                false, true, true, true)

        // Perform the resolution conflicts
        return dependenciesResolutionConflict(project, configurationContainer, false,
                true, LogLevel.DEBUG, CORE_DEPENDENCIES)
    }

    static Map<String, List<ArtifactDependency>> performResolutionConflicts(Project project, Configuration configToPerformResolution, boolean filterCoreDependency,
                                                                            boolean obtainSelectedArtifacts, boolean removeSourceModules=true) {
        def coreResolutionDependencies = performCoreResolutionConflicts(project, configToPerformResolution, removeSourceModules)

        ArtifactDependency coreArtifactDependency = null
        String currentCoreDependency = null

        // Filter the resolved core
        CoreMetadata coreMetadata = project.findProperty(CoreMetadata.CORE_METADATA_PROPERTY) as CoreMetadata

        if (coreMetadata) {
            currentCoreDependency = "${coreMetadata.coreGroup}:${coreMetadata.coreName}"
            coreArtifactDependency = ResolverDependencyUtils.getCoreDependency(project, currentCoreDependency, coreResolutionDependencies)

            // Update the CORE version
            if (coreArtifactDependency != null && !coreArtifactDependency.hasConflicts) {
                configToPerformResolution.resolutionStrategy.eachDependency({details ->
                    if ("${details.requested.group}:${details.requested.name}" == currentCoreDependency) {
                        details.useVersion(coreArtifactDependency.version)
                        details.because("CORE resolution strategy.")
                    }
                })
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

    static void loadCoreDependencies(Project project, CoreMetadata coreMetadata, Configuration container) {
        Configuration coreConfigurationContainer = project.configurations.create("coreConfigurationContainer")
        String coreArtifact = "${coreMetadata.coreGroup}:${coreMetadata.coreName}:${coreMetadata.coreVersion}"
        coreConfigurationContainer.dependencies.add(project.dependencies.create(coreArtifact))

        def incomingDependencies = getIncomingDependencies(project, coreConfigurationContainer,
                true, true, LogLevel.DEBUG)

        ResolverDependencyUtils.loadConfigurationWithArtifacts(project, container, incomingDependencies,
                false, true, false, true)
    }

}
