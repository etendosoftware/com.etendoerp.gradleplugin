package com.etendoerp.legacy.modules.expand

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.core.CoreMetadata
import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.legacy.ant.AntMenuHelper
import com.etendoerp.legacy.dependencies.ResolutionUtils
import com.etendoerp.legacy.dependencies.ResolverDependencyUtils
import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import com.etendoerp.legacy.dependencies.container.DependencyContainer
import com.etendoerp.legacy.dependencies.container.DependencyType
import com.etendoerp.publication.PublicationUtils
import groovy.io.FileType
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.logging.LogLevel

import java.util.function.Function

class ExpandUtils {

    final static String SOURCE_MODULES_CONTAINER = "sourceModulesContainer"
    final static String EXPAND_SOURCES_RESOLUTION_CONTAINER = "expandSourcesResolutionContainer"

    final static String PACKAGE_PROPERTY = "pkg"
    final static String FORCE_PROPERTY   = "force"

    final static String MODULE_NOT_FOUND_MESSAGE = "* The module to extract specified by the command line parameter '-P${PACKAGE_PROPERTY}' was not found"

    final static List<String> MODULES_CLASSIC = [
            'com.smf:smartclient.debugtools:[1.0.1,)@zip',
            'com.smf:smartclient.boostedui:[1.0.0,)@zip',
            'com.smf:securewebservices:[1.1.1,)@zip'
    ]

    static List<ArtifactDependency> getSourceModulesFiles(Project project, Configuration configuration, CoreMetadata coreMetadata) {
        def configurationToExpand = ResolverDependencyUtils.createRandomConfiguration(project,"expand", configuration)

        // TODO - Improvement: Exclude from the 'configuration' the modules specified by the user in the 'development' list. This will prevent downloading the sources.

        def extension = project.extensions.findByType(EtendoPluginExtension)
        def performResolutionConflicts = extension.performResolutionConflicts

        // Adds the classic modules if the Core is an old version
        addClassicModulesToCore(project, configurationToExpand, coreMetadata)

        def supportJars = coreMetadata.supportJars

        if (performResolutionConflicts) {
           def artifactDependencies = performExpandResolutionConflicts(project, coreMetadata, true, supportJars, true, true)
           configurationToExpand = ResolverDependencyUtils.updateConfigurationDependencies(project, configurationToExpand, artifactDependencies, true, false)
        }

        // Filter core dependencies
        ResolverDependencyUtils.excludeCoreDependencies(project, configurationToExpand, true)

        if (supportJars) {
            DependencyContainer dependencyContainer = new DependencyContainer(project, coreMetadata)
            dependencyContainer.configuration = configurationToExpand
            dependencyContainer.filterDependenciesFiles()
            return dependencyContainer.etendoDependenciesZipFiles.collect {it.value}
        } else {
            project.logger.info("* Getting incoming dependencies from the '${configurationToExpand.name}' configuration.")
            def incomingDependencies = ResolutionUtils.getIncomingDependencies(project, configurationToExpand, true, true, LogLevel.DEBUG)
            return collectDependenciesFiles(project, incomingDependencies, "zip", true)
        }

    }

    static void addClassicModulesToCore(Project project, Configuration configuration, CoreMetadata coreMetadata) {
        if (coreMetadata.coreGroup == CoreMetadata.CLASSIC_ETENDO_CORE_GROUP && coreMetadata.coreName == CoreMetadata.CLASSIC_ETENDO_CORE_NAME) {
            MODULES_CLASSIC.each {
                project.dependencies.add(configuration.name, it)
            }
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
    static List<ArtifactDependency> collectDependenciesFiles(Project project, Map<String, List<ArtifactDependency>> dependencies, String extension, boolean ignoreCore) {
        List<ArtifactDependency> collection = new ArrayList<>()

        for (def entry : dependencies.entrySet()) {

            // Verify that the artifact list exists and contains a value.
            List<ArtifactDependency> artifactList = entry.value
            if (!artifactList || !(artifactList.size() >= 1)) {
                continue
            }

            // Get the 'selected' artifact version.
            String displayName = artifactList.get(0).displayName

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

    static ArtifactDependency collectArtifactDependencyFile(Project project, String displayName, String extension, boolean printTrace=false) {
        String dependency = "${displayName}@${extension}"
        ArtifactDependency artifactDependency = null
        try {
            project.logger.info("")
            project.logger.info("* Trying to resolve the dependency: ${dependency}")

            // Create a custom configuration container
            def configurationContainer = project.configurations.create(UUID.randomUUID().toString().replace("-",""))

            // Add module dependency
            project.dependencies.add(configurationContainer.name, dependency)

            // Resolve artifact
            def resolvedArtifact = configurationContainer.resolvedConfiguration.resolvedArtifacts

            // The resolved artifact should be only one
            for (ResolvedArtifact artifact : resolvedArtifact) {
                artifactDependency = DependencyContainer.getArtifactDependency(project, artifact)
            }

            if (artifactDependency) {
                project.logger.info("Dependency resolved: ${artifactDependency.resolvedArtifact.getId()}")
            }

        } catch (Exception e) {
            project.logger.error("The dependency ${dependency} could not be resolved.")
            project.logger.error(e.getMessage())
            if (printTrace) {
                e.printStackTrace()
            }
        }

        return artifactDependency
    }

    static Map<String, List<ArtifactDependency>> performExpandResolutionConflicts(Project project, CoreMetadata coreMetadata, boolean addCoreDependency, boolean addProjectDependencies, boolean filterCoreDependency, boolean obtainSelectedArtifacts) {
        // Create custom configuration container
        def resolutionContainer = ResolverDependencyUtils.createRandomConfiguration(project, EXPAND_SOURCES_RESOLUTION_CONTAINER)
        def resolutionDependencySet = resolutionContainer.dependencies

        if (addCoreDependency) {
            // Add the current core version
            def core = "${coreMetadata.coreGroup}:${coreMetadata.coreName}:${coreMetadata.coreVersion}"
            project.logger.info("* Adding the core dependency to perform resolution conflicts. ${core}")
            project.dependencies.add(resolutionContainer.name, core)
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
        return ResolutionUtils.performResolutionConflicts(project, resolutionContainer, filterCoreDependency, obtainSelectedArtifacts)
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
        Function<String, Boolean> filterFunction = generateFilterFunction(project, configuration)

        List extractedArtifacts = []
        List<ArtifactDependency> artifactToExtract = []
        List<ArtifactDependency> artifactToIgnore = []

        artifactDependencies.stream().filter({artifact ->
            artifact.type == DependencyType.ETENDOZIPMODULE
        }).forEach({artifact ->
            if (filterFunction.apply(artifact.moduleName)) {
                artifactToExtract.add(artifact)
            } else {
                artifactToIgnore.add(artifact)
            }
        })

        if (shouldExpandSourceModules(project, artifactToExtract, artifactToIgnore)) {
            artifactToExtract.stream().forEach({ artifact ->
                artifact.extract()
                extractedArtifacts.add(artifact.moduleName)
            })
        }

        project.logger.info("************** Extracted modules: ${extractedArtifacts.size()} **************")
        extractedArtifacts.stream().forEach({ String name ->
            project.logger.info("* Module: ${name}")
        })
        project.logger.info("**************************************************")

        // Verify pkg property passed by the user
        String pkgName = project.findProperty(PACKAGE_PROPERTY)
        if (pkgName && !(extractedArtifacts.stream().anyMatch({String name -> name.equalsIgnoreCase(pkgName)}))) {
            throw new IllegalArgumentException("${MODULE_NOT_FOUND_MESSAGE} - '${pkgName}'")
        }
    }

    static Function<String, Boolean> generateFilterFunction(Project project, Configuration configuration) {
        def dependencyMap = ResolverDependencyUtils.loadDependenciesMap(project, configuration)
        def sourceModulesMap = getSourceModules(project)

        // Check if the users provide the -Ppkg flag
        String pkgName = project.findProperty(PACKAGE_PROPERTY)
        String forceProp = project.findProperty(FORCE_PROPERTY)
        Boolean overwrite = project.extensions.findByType(EtendoPluginExtension).overwriteTransitiveExpandModules
        List<String> sourceModulesInDevelopment = project.extensions.findByType(EtendoPluginExtension).sourceModulesInDevelopment

        Function<String, Boolean> filterFunction

        if (pkgName) {
            filterFunction = filterArtifactByPackage(pkgName, dependencyMap)
        } else {
            filterFunction = filterArtifact(dependencyMap, sourceModulesMap, sourceModulesInDevelopment, forceProp, overwrite)
        }

        return filterFunction
    }

    /**
     * Generates the lambda function used to filter the module passed by the user as a command line parameter
     * @param pkgName
     * @param dependencyMap
     * @return
     */
    static Function<String, Boolean> filterArtifactByPackage(String pkgName, Map<String, Dependency> dependencyMap) {
        return { moduleName ->
            return moduleName.equalsIgnoreCase(pkgName) && dependencyMap.containsKey(moduleName)
        }
    }

    static boolean shouldExpandSourceModules(Project project, List<ArtifactDependency> artifactToExtract, List<ArtifactDependency> artifactToIgnore) {
        def choiceExpand = "1"
        def options = [
                (choiceExpand) :"Expand source modules",
        ]

        String preMessage = generatePreMessage(project, artifactToExtract, artifactToIgnore)

        def userChoice = AntMenuHelper.antMenu(
                project,
                "Select an option",
                options,
                preMessage,
                null,
                "The modules will not be expanded."
        )

        return userChoice == choiceExpand
    }

    static String generatePreMessage(Project project, List<ArtifactDependency> artifactToExtract, List<ArtifactDependency> artifactToIgnore) {
        String preMessage = "\n"

        preMessage += "********** SOURCE MODULES TO NOT EXPAND: ${artifactToIgnore ? artifactToIgnore.size() : "0"} ********** \n"
        preMessage += generateListOfModulesNames(artifactToIgnore)
        preMessage += EtendoPluginExtension.sourceModulesInDevelopMessage()
        preMessage += "***************************************************** \n"
        preMessage += "- \n"
        preMessage += "************ SOURCE MODULES TO EXPAND: ${artifactToExtract ? artifactToExtract.size() : "0"} ************ \n"
        preMessage += generateListOfModulesNames(artifactToExtract)
        preMessage += "***************************************************** \n"

        preMessage += "*** WARNING: The expanded modules will overwrite the sources ones. \n"
        return preMessage
    }

    static String generateListOfModulesNames(List<ArtifactDependency> artifacts) {
        String moduleList = ""

        if (artifacts && !artifacts.isEmpty()) {
            artifacts.stream().forEach({ artifact ->
                moduleList += "* Module: ${artifact.moduleName} ${artifact.version ? "- Version: ${artifact.version}" : ""}\n"
            })
        }

        return moduleList
    }

    /**
     * Generates the lambda function used to filter the source module to extrad based on:
     * If the module is not in sources, then MUST be extracted.
     * If the module is in the 'moduleDeps' config:
     *  If the user provides the force flag, then should be ALWAYS extracted.
     *  If the module is already in sources, then is NOT extracted.
     *
     * If the module is not in the 'moduleDeps' then is a transitive module.
     * Transitive modules by default are overwritten, unless the user specifies the overwrite flag to false.
     *
     * @param dependencyMap
     * @param sourceModuleMap
     * @param force
     * @param overwrite
     * @return
     */
    static Function<String, Boolean> filterArtifact(Map<String, Dependency> dependencyMap, Map<String, File> sourceModuleMap, List<String> sourceModulesInDevelopment, String force, Boolean overwrite) {
        return { moduleName ->
            def isInSources = sourceModuleMap.containsKey(moduleName)
            def isInModuleDeps = dependencyMap.containsKey(moduleName)
            def isInWhiteList = sourceModulesInDevelopment.stream().anyMatch({String name -> name.equalsIgnoreCase(moduleName)})

            if (isInWhiteList) {
                return false
            }

            def extract = false

            // If the module is not in sources then should be extracted
            if (!isInSources) {
                extract = true
            } else {
                if (isInModuleDeps) {
                    extract = true
                } else {
                    // Transitive (not defined in the 'moduleDeps' config)
                    if (overwrite) {
                        extract = true
                    }
                }
            }

            return extract
        }
    }

    static Map<String, File> getSourceModules(Project project) {
        Map<String, File> sourceModules = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
        def modulesLocation = new File(project.rootDir, PublicationUtils.BASE_MODULE_DIR)

        if (!modulesLocation.exists()) {
            return sourceModules
        }

        // Add the source modules
        modulesLocation.traverse(type: FileType.DIRECTORIES, maxDepth: 0) {
            sourceModules.put(it.name, it)
        }
        return sourceModules
    }

}
