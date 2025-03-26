package com.etendoerp.modules

import com.etendoerp.gradleutils.GradleUtils
import com.etendoerp.gradleutils.ProjectProperty
import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.configuration.pom.PomConfigurationContainer
import com.etendoerp.publication.configuration.pom.PomProjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency

class ModulesConfigurationUtils {

    static final String DEFAULT_CONFIG_COPY = "defaultCopy"

    /**
     * Configure each subproject with the POM container which holds the defined dependencies,
     * the copy of the 'default' configuration and the 'ProjectDependency' used later to perform the resolution of conflicts.
     * @param mainProject
     */
    static void configureSubprojects(Project mainProject) {
        def moduleProject = mainProject.findProject(":${PublicationUtils.BASE_MODULE_DIR}")

        // Contains a map between the subproject name "group:artifact" and the subproject
        def subprojectNames = loadSubprojectsNames(mainProject)

        // Configure the main project
        configureMainProject(mainProject, subprojectNames)

        if (moduleProject) {
            // Configure subprojects
            moduleProject.subprojects.each {
                configureSubproject(mainProject, it, subprojectNames)
            }
        }
    }

    static void configureMainProject(Project mainProject, Map<String, Project> subprojectNamesMap) {
        DefaultConfiguration defaultCopy = mainProject.configurations.default.copyRecursive() as DefaultConfiguration
        mainProject.configurations.add(defaultCopy)

        configurePomContainer(mainProject, mainProject, subprojectNamesMap)
    }

    static void configureSubproject(Project mainProject, Project subProject, Map<String, Project> subprojectNamesMap) {
        configurePomContainer(mainProject, subProject, subprojectNamesMap)

        def validConfigs = subProject.configurations.findAll().collect()

        configureSubstitutions(mainProject, validConfigs, subprojectNamesMap, "Source module already present.")
    }

    /**
     * Configures a List of {@link Configuration} to replace a dependency using a Source subproject
     * @param mainProject
     * @param configurations List of configurations to apply the dependency substitution
     * @param subprojectSubstitutions Map of the Source subproject used to substitute a Dependency. The key has to be in the 'group:artifact' notation.
     * @param becauseReason
     */
    static void configureSubstitutions(Project mainProject, List<Configuration> configurations, Map<String, Project> subprojectSubstitutions, String becauseReason = "") {
        configurations.each { configuration ->
            configuration.resolutionStrategy.dependencySubstitution { dep ->
                subprojectSubstitutions.each { key, project ->
                    dep.substitute(dep.module(key))
                            .because(becauseReason)
                            .using(dep.project(project.path))
                }
            }
        }
    }

    /**
     * Configures each subproject to use a common dependency version to prevent downloading unused dependencies.
     * @param mainProject
     * @param dependencyMap
     */
    static void configureVersionReplacer(Project mainProject, Map<String, ArtifactDependency> dependencyMap) {
        // Contains a map between the subproject name "group:artifact" and the subproject
        Map<String, Project> subprojectNames = getSubprojectNames(mainProject)

        subprojectNames.each {
            Project subProject = it.value
            def validConfigs = subProject.configurations.findAll {
                it.name in DependencyUtils.VALID_CONFIGURATIONS
            }.collect()
            configureVersionReplacer(mainProject, validConfigs, dependencyMap)
        }
    }

    static void configureVersionReplacer(Project mainProject, List<Configuration> configurations, Map<String, ArtifactDependency> dependencyMap) {
        configurations.each {
            it.resolutionStrategy.eachDependency({details ->
                String dependencyName = "${details.requested.group}:${details.requested.name}"
                if (dependencyMap.containsKey(dependencyName)) {
                    details.useVersion(dependencyMap.get(dependencyName).version)
                    details.because("Dependency resolution.")
                }
            })
        }
    }

    static void configurePomContainer(Project mainProject, Project subProject, Map<String, Project> subprojectNamesMap) {
        def pomContainer = PomConfigurationContainer.getPomContainer(mainProject, subProject)

        // Copy of the default configuration
        def defaultConfig = subProject.configurations.findByName(DEFAULT_CONFIG_COPY)

        if (defaultConfig) {
            pomContainer.defaultCopyConfiguration = defaultConfig

            // Only allow user declared dependencies or project dependencies
            defaultConfig.dependencies.removeIf({
                !(it instanceof DefaultExternalModuleDependency || it instanceof DefaultProjectDependency)
            })

            List<Configuration> configs = [defaultConfig] as List<Configuration>

            def subConfContainer = subProject.configurations.findByName(PomConfigurationContainer.SUBPROJECT_DEPENDENCIES_CONFIGURATION_CONTAINER)
            if (subConfContainer) {
                configs.add(subConfContainer)
            }

            loadPomSubprojectDependencies(mainProject, pomContainer, configs, subprojectNamesMap)

            // Create the 'DefaultProjectDependency'
            DefaultProjectDependency projectDependency = mainProject.dependencies.create(subProject) as DefaultProjectDependency
            projectDependency.setTargetConfiguration(defaultConfig.name)
            pomContainer.projectDependency = projectDependency
        }
    }

    static void loadPomSubprojectDependencies(Project mainProject, PomConfigurationContainer pomContainer,
                                           List<Configuration> configurations, Map<String, Project> subprojectNamesMap) {
        for (Configuration configuration : configurations) {
            configuration.allDependencies.each {
                String name = it.group + ":" + it.name
                String version = it.version
                PomProjectContainer projectContainer = new PomProjectContainer(it, name, version)

                if (subprojectNamesMap.containsKey(name)) {
                    projectContainer.projectDependency = subprojectNamesMap.get(name)
                    projectContainer.isProjectDependency = true
                }

                pomContainer.putSubproject(projectContainer)
            }
        }
    }

    /**
     * Loads the Map between the project name 'group:artifact' and the {@link Project}
     * using the {@link ProjectProperty} SOURCE_MODULES_MAP
     *
     * @param mainProject
     * @return Map between the project name 'group:artifact' and the {@link Project}
     */
    static Map<String, Project> loadSubprojectsNames(Project mainProject) {
        return GradleUtils.loadProjectProperty(mainProject, mainProject, ProjectProperty.SOURCE_MODULES_MAP, loadSubprojectsNamesMap(mainProject))
    }

    /**
     * Creates a Map between the project name 'group:artifact' and the {@link Project}
     * @param mainProject
     * @return
     */
    static Map<String, Project> loadSubprojectsNamesMap(Project mainProject) {
        Map<String, Project> subprojectNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)

        def moduleProject = mainProject.findProject(":${PublicationUtils.BASE_MODULE_DIR}")

        if (moduleProject) {
            moduleProject.subprojects.each {
                def subprojectNameOptional = getSubprojectName(mainProject, it)
                if (subprojectNameOptional.isPresent()) {
                    subprojectNames.put(subprojectNameOptional.get(), it)
                }
            }
        }

        return subprojectNames
    }

    /**
     * Returns a Map between the project name (group:name) and the Project itself
     * @param mainProject
     * @return
     */
    static Map<String, Project> getSubprojectNames(Project mainProject) {
        // Contains a map between the subproject name "group:artifact" and the subproject
        Map<String, Project> subprojectNames

        def subprojectNamesOptional = GradleUtils.getProjectProperty(mainProject, mainProject, ProjectProperty.SOURCE_MODULES_MAP)
        if (subprojectNamesOptional.isPresent()) {
            subprojectNames = subprojectNamesOptional.get() as Map<String, Project>
        } else {
            subprojectNames = loadSubprojectsNames(mainProject)
        }
        return subprojectNames
    }

    static Optional<String> getSubprojectName(Project mainProject, Project subProject) {
        if (ModuleUtils.isValidSubproject(mainProject, subProject)) {
            return Optional.of("${subProject.group}:${subProject.artifact}".toString())
        }
        return parseSubprojectName(mainProject, subProject)
    }

    static Optional<String> getSubprojectNameFromPath(Project mainProject, String subProjectPath) {
        def subProject = mainProject.findProject(subProjectPath)
        if (subProject) {
            if (ModuleUtils.isValidSubproject(mainProject, subProject)) {
                return Optional.of("${subProject.group}:${subProject.artifact}".toString())
            }
            return parseSubprojectName(mainProject, subProject)
        }
        return Optional.empty()
    }

    static Optional<String> parseSubprojectName(Project mainProject, Project subProject) {
        def group = splitSubprojectGroup(mainProject, subProject.projectDir.name)
        def artifact = splitSubprojectArtifact(mainProject, subProject.projectDir.name)

        if (group.isPresent() && artifact.isPresent()) {
            return Optional.of(group.get()+":"+artifact.get())
        }
        Optional.empty()
    }

    static Optional<String> splitSubprojectGroup(Project mainProject, String subprojectName) {
        ArrayList<String> parts = subprojectName.split('\\.')
        if (parts.size() >= 2) {
            return Optional.of(parts[0]+"."+parts[1])
        }
        return Optional.empty()
    }

    static Optional<String> splitSubprojectArtifact(Project mainProject, String subprojectName){
        ArrayList<String> parts = subprojectName.split("\\.")
        if (parts.size() >= 2) {
            parts= parts.subList(2, parts.size())
            return Optional.of(parts.join("."))
        }
        return Optional.empty()
    }
}
