package com.etendoerp.modules

import com.etendoerp.gradleutils.GradleUtils
import com.etendoerp.gradleutils.ProjectProperty
import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.configuration.pom.PomConfigurationContainer
import com.etendoerp.publication.configuration.pom.PomProjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency

class ModulesConfigurationUtils {

    static final String DEFAULT_CONFIG_COPY = "defaultCopy"

    static void configureSubprojects(Project mainProject) {
        def moduleProject = mainProject.findProject(":${PublicationUtils.BASE_MODULE_DIR}")

        if (moduleProject) {
            // Contains a map between the subproject name "group:artifact" and the subproject
            def subprojectNames = loadSubprojectsNames(mainProject)
            moduleProject.subprojects.each {
                configureSubproject(mainProject, it, subprojectNames)
            }
        }
    }

    static void configureSubproject(Project mainProject, Project subProject, Map<String, Project> subprojectNamesMap) {
        configurePomContainer(mainProject, subProject, subprojectNamesMap)

        DependencyUtils.VALID_CONFIGURATIONS

        def validConfigs = subProject.configurations.findAll {
            it.name in DependencyUtils.VALID_CONFIGURATIONS || it.name == DEFAULT_CONFIG_COPY
        }.collect()

        configureSubstitutions(mainProject, validConfigs, subprojectNamesMap, "Source module already present.")
    }

    /**
     * Configures a List of {@link Configuration} to replace a dependency using a Source subproject
     * @param mainProject
     * @param configurations List of configurations to apply the dependency substitution
     * @param subprojectSubstitutions Map of the Source subproject used to substitute a Dependency. The key has to be in the 'group:artifact' notation.
     * @param becauseReason
     */
    static void configureSubstitutions(Project mainProject, List<Configuration> configurations, Map<String, Project> subprojectSubstitutions, String becauseReason="") {
        configurations.each {
            it.resolutionStrategy.dependencySubstitution({dep ->
                subprojectSubstitutions.each {
                    dep.substitute(dep.module(it.key)).because(becauseReason).with(dep.project(it.value.path))
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
            List<Configuration> configs = [defaultConfig,
                                           subProject.configurations.findByName(PomConfigurationContainer.SUBPROJECT_DEPENDENCIES_CONFIGURATION_CONTAINER)] as List<Configuration>
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
                if (ModuleUtils.isValidSubproject(mainProject, it)) {
                    subprojectNames.put("${it.group}:${it.artifact}".toString(), it)
                } else {
                    def subprojectName = parseSubprojectName(mainProject, it)
                    if (subprojectName.isPresent()) {
                        subprojectNames.put(subprojectName.get(), it)
                    }
                }
            }
        }

        return subprojectNames
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
