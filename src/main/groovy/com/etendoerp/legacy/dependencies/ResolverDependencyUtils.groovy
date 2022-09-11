package com.etendoerp.legacy.dependencies

import com.etendoerp.core.CoreMetadata
import com.etendoerp.gradleutils.GradleUtils
import com.etendoerp.gradleutils.ProjectProperty
import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import com.etendoerp.modules.ModulesConfigurationUtils
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.configuration.pom.PomConfigurationContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.internal.artifacts.dependencies.AbstractModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy

class ResolverDependencyUtils {

    static Configuration loadResolutionDependencies(Project mainProject) {
        Configuration resolutionConfiguration = createRandomConfiguration(mainProject, "resolution")

        // Contains a map between the subproject name "group:artifact" and the subproject
        Map<String, Project> subprojectNames = ModulesConfigurationUtils.getSubprojectNames(mainProject)

        // Obtain the main project dependencies
        def mainConf = mainProjectDependencies(mainProject, subprojectNames)
        if (mainConf) {
            resolutionConfiguration.extendsFrom(mainConf)
            resolutionConfiguration.dependencies.addAll(mainConf.dependencies)
        }

        // Obtain the subproject dependencies
        def moduleProject = mainProject.findProject(":${PublicationUtils.BASE_MODULE_DIR}")

        if (moduleProject) {
            moduleProject.subprojects.each {
                def subprojectPom = PomConfigurationContainer.getPomContainer(mainProject, it)

                // Add the 'projectDependency' to the resolutionConfiguration
                if (subprojectPom.projectDependency) {
                    resolutionConfiguration.dependencies.add(subprojectPom.projectDependency)
                }
            }
        }

        // Configure the resolutionConfiguration to substitute all the dependencies
        // already in sources.
        ModulesConfigurationUtils.configureSubstitutions(mainProject, [resolutionConfiguration], subprojectNames)

        return resolutionConfiguration
    }

    static Configuration mainProjectDependencies(Project mainProject, Map<String, Project> subprojectNames) {
        Configuration configuration = null

        // Obtain the main project dependencies
        def mainPom = PomConfigurationContainer.getPomContainer(mainProject, mainProject)
        if (mainPom.defaultCopyConfiguration) {
            configuration = mainPom.defaultCopyConfiguration
        }

        // Exclude from the root configurations the dependencies already in sources
        def validConfigs = mainProject.configurations.findAll {
            it.name in DependencyUtils.VALID_CONFIGURATIONS || it.name == ModulesConfigurationUtils.DEFAULT_CONFIG_COPY
        }.collect()

        validConfigs.each { Configuration conf ->
            conf.dependencies.removeIf({
                String dependencyName = "${it.group}:${it.name}"
                return subprojectNames.containsKey(dependencyName)
            })

            subprojectNames.each {
                def nameSplit = it.key.split(":")
                if (nameSplit.size() >= 2) {
                    conf.exclude([group: nameSplit[0], module: nameSplit[1]])
                }
            }
        }

        return configuration
    }

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

    static Configuration loadSubprojectDependencies(Project mainProject, Project subProject, List<String> configurationsToSearch=[], boolean onlyExternalDependencies=true) {
        def subProjectConfigurations = DependencyUtils.loadListOfConfigurations(subProject, configurationsToSearch)

        def container = subProject.configurations.create(UUID.randomUUID().toString().replace("-",""))
        DependencySet containerSet = container.dependencies

        // Create a DependencySet with all the dependencies from the subproject
        DependencyUtils.loadDependenciesFromConfigurations(subProjectConfigurations, containerSet, onlyExternalDependencies)
        return container
    }

    static Configuration loadSubprojectDefaultDependencies(Project mainProject, Project subProject, boolean onlyExternalDependencies=true) {
        return loadSubprojectDependencies(mainProject, subProject, DependencyUtils.VALID_CONFIGURATIONS, onlyExternalDependencies)
    }


    /**
     * Loads the dependencies map with the module name has key and the Dependency has value.
     * @param container
     */
    static Map<String, Dependency> loadDependenciesMap(Project project, Configuration container, String separator=".") {
        Map<String, Dependency> dependenciesMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
        for (Dependency dependency : container.dependencies) {
            def group = dependency.group
            def name = dependency.name
            if (dependency instanceof DefaultProjectDependency) {
                DefaultProjectDependency projectDependency = dependency as DefaultProjectDependency
                def artifact = projectDependency.getDependencyProject().findProperty("artifact")
                if (artifact) {
                    name = artifact
                }
            }
            String moduleName = "${group}${separator}${name}"
            dependenciesMap.put(moduleName, dependency)
        }
        return dependenciesMap
    }


    /**
     * Creates a custom Configuration loaded with the ArtifactDependencies and Configurations passed has parameter.
     * @param project
     * @param configurations
     * @param artifactDependencyMap
     * @return
     */
    static Configuration createConfigurationFromArtifacts(Project project, List<Configuration> configurations, Map<String, List<ArtifactDependency>> artifactDependencyMap) {

        def configurationContainer = createRandomConfiguration(project)
        def configurationDependencySet = configurationContainer.dependencies

        // Load the configurations
        DependencyUtils.loadDependenciesFromConfigurations(configurations, configurationDependencySet)

        // Load the Artifact dependencies
        loadConfigurationWithArtifacts(project, configurationContainer, artifactDependencyMap,
                true, true, false, false)

        return configurationContainer
    }

    /**
     * Loads a Configuration with artifacts or updates the dependencies if they are already present.
     *
     * This is used by the 'conflict resolution' to add all the Etendo dependencies and verify if the CORE has conflicts.
     * Also is used to update the 'final' configuration which will be expanded (sources or jar files). Each version dependency
     * is updated based on the result of the resolution conflict.
     *
     * @param project
     * @param configuration
     * @param artifacts
     * @param updateConstrains
     * @param addMissingArtifact
     */
    static void loadConfigurationWithArtifacts(Project project, Configuration configuration, Map<String, List<ArtifactDependency>> artifacts,
                                               boolean updateConstrains=false, boolean addMissingArtifact=true, boolean isTransitive=false, boolean reloadArtifact=false) {
        for (def entry : artifacts.entrySet()) {
            for (ArtifactDependency artifactDependency : entry.value) {
                String displayName = artifactDependency.displayName
                Set<DefaultExternalModuleDependency> dependencies = filterDependenciesByName(project, configuration, artifactDependency.group, artifactDependency.name)
                if (dependencies && !dependencies.isEmpty() && !reloadArtifact) {
                    if (updateConstrains) {
                        updateDependenciesVersion(project, dependencies, artifactDependency.dependencyResult.selected.getModuleVersion().version)
                    }
                } else if (displayName && (addMissingArtifact || reloadArtifact)) {
                    configuration.dependencies.add(project.dependencies.create(displayName, {
                        transitive = isTransitive
                    }))
                }
            }
        }
    }

    static void updateDependenciesVersion(Project project, Set<DefaultExternalModuleDependency> dependencies, String version, boolean overwrite=false) {
        dependencies.each {
            def versionConstraint = it.versionConstraint
            if (overwrite || (!it.force && versionConstraint.strictVersion.isBlank()
                    && versionConstraint.preferredVersion.isBlank()
                    && !(version in versionConstraint.rejectedVersions))) {
                it.versionConstraint.requiredVersion = version
            }
        }
    }

    static Set<DefaultExternalModuleDependency> filterDependenciesByName(Project project, Configuration configuration, String group, String name) {
        return configuration.dependencies.findAll {
            it instanceof DefaultExternalModuleDependency && it.group == group && it.name == name
        } as Set<DefaultExternalModuleDependency>
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
    static Configuration updateConfigurationDependencies(Project project, Configuration configuration, Map<String, List<ArtifactDependency>> artifactDependencyMap, boolean filterCoreDependency, boolean updateOnConflicts) {
        // Obtain the incoming 'requested' dependencies from the Configuration
        project.logger.info("* Getting incoming dependencies from the configuration '${configuration.name}' to be updated.")
        def incomingDependencies = ResolutionUtils.getIncomingDependenciesExcludingCore(project, configuration, filterCoreDependency, false)

        // Update the dependencies of the incomingDependencies
        for (def entry : artifactDependencyMap.entrySet()) {
            String dependencyName = entry.key
            List<ArtifactDependency> artifactList = entry.value

            if (!incomingDependencies.containsKey(dependencyName)) {
                continue
            }

            // Get the selected dependency
            ArtifactDependency selectedDependency = null

            if (artifactList && artifactList.size() >= 1 ) {
                selectedDependency = artifactList.get(0)
            }

            if (!selectedDependency) {
                continue
            }

            if (selectedDependency.hasConflicts && !updateOnConflicts) {
                continue
            }

            // Update the dependencies list
            incomingDependencies.put(dependencyName, artifactList)
        }

        return createConfigurationFromArtifacts(project, [configuration], incomingDependencies)
    }

    static void excludeDependencies(Project project, Configuration configuration, List<ArtifactDependency> dependenciesToExclude, boolean removeFromConfiguration) {
        for (ArtifactDependency dependency : dependenciesToExclude) {
            String group = dependency.group
            String name  = dependency.name
            String artifactName = "${group}:${name}"

            if (removeFromConfiguration) {
                configuration.dependencies.removeIf({
                    String dependencyName = "${it.group}:${it.name}"
                    return dependencyName.contains(artifactName)
                })
            }

            // Exclude transitives dependencies
            configuration.exclude([group : "$group", module: "$name"])
        }
    }

    static void excludeCoreDependencies(Project project, Configuration configuration, boolean removeFromConfiguration) {
        ArtifactDependency defaultCore = new ArtifactDependency(project, CoreMetadata.DEFAULT_ETENDO_CORE_GROUP, CoreMetadata.DEFAULT_ETENDO_CORE_NAME, "1.0.0")
        ArtifactDependency classicCore = new ArtifactDependency(project, CoreMetadata.CLASSIC_ETENDO_CORE_GROUP, CoreMetadata.CLASSIC_ETENDO_CORE_NAME, "1.0.0")
        excludeDependencies(project, configuration, [defaultCore, classicCore], removeFromConfiguration)
    }

    static void excludeCoreFromDependencies(Project project, Configuration configuration) {
        configuration.dependencies.each {
            if (it instanceof AbstractModuleDependency) {
                AbstractModuleDependency dependency = it as AbstractModuleDependency
                dependency.exclude(group: CoreMetadata.DEFAULT_ETENDO_CORE_GROUP, module: CoreMetadata.DEFAULT_ETENDO_CORE_NAME)
                dependency.exclude(group: CoreMetadata.CLASSIC_ETENDO_CORE_GROUP, module: CoreMetadata.CLASSIC_ETENDO_CORE_NAME)
            }
        }
    }

    /**
     * Creates a random Configuration.
     * If the 'configurationToAdd' is passed has a parameter, loads all the dependencies to the new configuration.
     * @param project
     * @param configurationToAdd
     * @return
     */
    static Configuration createRandomConfiguration(Project project, String name = null, Configuration configurationToAdd = null) {
        String configName = (name) ?: "internal"

        def config = project.configurations.create("${configName}-configuration-" + UUID.randomUUID().toString().replace("-",""))
        if (configurationToAdd) {
            DependencyUtils.loadDependenciesFromConfigurations([configurationToAdd], config.dependencies)
        }
        return config
    }

    static Configuration createExtendedConfiguration(Project project, String name=null, Configuration confToExtend=null,
                                                     boolean addSubstitutionsRules=true, boolean addDependencies=true) {
        String configName = (name) ?: "internal"
        def config = project.configurations.create("${configName}-configuration-" + UUID.randomUUID().toString().replace("-",""))

        if (confToExtend) {
            config.extendsFrom(confToExtend)
            if (addSubstitutionsRules && confToExtend.resolutionStrategy instanceof DefaultResolutionStrategy ) {
                config.resolutionStrategy.dependencySubstitution.all(
                        (confToExtend.resolutionStrategy as DefaultResolutionStrategy ).getDependencySubstitutionRule()
                )
            }

            if (addDependencies) {
                DependencyUtils.loadDependenciesFromConfigurations([confToExtend], config.dependencies)
            }
        }

        return config
    }

    /**
     * Obtains the Core dependency from the artifact list
     * @param project
     * @param name
     * @param artifactList
     * @return
     */
    static ArtifactDependency getCoreDependency(Project project, String name, Map<String, List<ArtifactDependency>> artifactListMap) {
        ArtifactDependency coreArtifact = null

        List<ArtifactDependency> artifactList = artifactListMap.get(name)

        if (artifactList && artifactList.size() >= 1) {
            coreArtifact = artifactList.get(0)
        }

        return coreArtifact
    }

}
