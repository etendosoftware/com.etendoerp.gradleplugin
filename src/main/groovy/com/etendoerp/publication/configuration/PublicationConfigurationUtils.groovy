package com.etendoerp.publication.configuration

import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.legacy.dependencies.ResolverDependencyUtils
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.configuration.pom.PomConfigurationContainer
import com.etendoerp.publication.configuration.pom.PomConfigurationType
import com.etendoerp.publication.configuration.pom.PomProjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

class PublicationConfigurationUtils {

    static <V> List<V> queueToList(Queue<Map.Entry<?, V>> queue) {
        List processedProjectList = []
        while (!queue.isEmpty()) {
            def processedEntry = queue.poll()
            processedProjectList.add(processedEntry.value)
        }
        return processedProjectList
    }

    static <K,V> Map<K,V> queueToMap(Queue<Map.Entry<K,V>> queue) {
        Map<K,V> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER as Comparator<? super K>)
        while (!queue.isEmpty()) {
            def processedEntry = queue.poll()
            map.put(processedEntry.key, processedEntry.value)
        }
        return map
    }

    /**
     * Load the subproject dependencies.
     * The dependencies could belong from the 'java' plugin configurations (implementation),
     * or specified by the user in a custom configuration.
     *
     * The 'java' dependencies and the user ones are separated in different properties and stored
     * in each subproject as a Map.
     *
     * @param mainProject
     * @param subProjects List of subproject.
     * @param pomType Contains the name of the properties and configurations to search and store dependencies.
     * @param extraConfigurations List of extra configurations to search dependencies, by default is a empty list.
     */
    static void loadSubprojectDependencies(Project mainProject, List<Project> subProjects, PomConfigurationType pomType, List<String> extraConfigurations=[], boolean loadDefaultDependencies=true) {
        for (Project subproject : subProjects) {
            // Search and load the configurations passed by parameter
            def configurationsToSearch = [pomType.externalConfiguration] + extraConfigurations

            def container = ResolverDependencyUtils.loadSubprojectDependencies(mainProject, subproject, configurationsToSearch, true)
            def dependencies = ResolverDependencyUtils.loadDependenciesMap(subproject, container)
            subproject.ext.set(pomType.internalDependenciesProperty, dependencies)

            // Search and load the default configurations from the 'java' plugin
            if (loadDefaultDependencies) {
                def defaultContainer = ResolverDependencyUtils.loadSubprojectDefaultDependencies(mainProject, subproject, true)
                def defaultDependencies = ResolverDependencyUtils.loadDependenciesMap(subproject, defaultContainer)
                subproject.ext.set(PublicationConfiguration.DEFAULT_DEPENDENCIES_CONTAINER, defaultDependencies)
            }
        }
    }

    /**
     * Verifies which are the subproject dependencies.
     * In case of the 'subproject' being dependency of another subproject(parent), a special configuration
     * is created and populated with the 'subproject' information. This is used later to update the POM file.
     *
     * The 'parent' subprojects are added to a custom list to be returned.
     *
     * @param mainProject
     * @param subProject
     * @param moduleSubprojects
     * @param pomType
     * @return
     */
    static List<Project> verifyProjectDependency(Project mainProject, Project subProject, List<Project> moduleSubprojects, PomConfigurationType pomType) {
        List<Project> projectDependencies = []
        String subprojectGroup    = subProject.group as String
        String subprojectArtifact = subProject.artifact as String
        String subprojectName = "${subprojectGroup}.${subprojectArtifact}"

        String dependencyContainerProperty = pomType.getInternalDependenciesProperty()
        String configurationContainerProperty = pomType.getInternalConfigurationProperty()

        // Check if the 'subProject' is dependency of any 'moduleSubproject'
        for (Project moduleSubproject : moduleSubprojects) {
            // Contains the dependencies set using the 'java' plugin configurations (implementation, runtime, etc.)
            Map<String, Dependency> defaultDependencyMap = moduleSubproject.findProperty(PublicationConfiguration.DEFAULT_DEPENDENCIES_CONTAINER) as Map<String, Dependency>

            // Contains the dependencies specified by the user (in the build.gradle) using a custom configuration.
            Map<String, Dependency> dependencyMap = moduleSubproject.findProperty(dependencyContainerProperty) as Map<String, Dependency>

            boolean isDependency = false
            Dependency dependency = null

            // Check on the default dependencies using the 'java' plugin configurations
            if (defaultDependencyMap && defaultDependencyMap.containsKey(subprojectName)) {
                DependencyUtils.removeDependencyFromSubproject(mainProject, moduleSubproject, subprojectGroup, subprojectArtifact)
                dependency = defaultDependencyMap.get(subprojectName)
                isDependency = true
            } else if (dependencyMap && dependencyMap.containsKey(subprojectName)) {
                dependency = dependencyMap.get(subprojectName)
                isDependency = true
            }

            // Add the 'subproject' dependency to the 'moduleSubproject' custom configuration
            // Used later in the POM to update the correct version
            if (isDependency) {
                addDependencyToProject(mainProject, moduleSubproject, subProject, configurationContainerProperty, dependency, pomType)
                projectDependencies.add(moduleSubproject)
            }
        }
        return projectDependencies
    }

    static void addDependencyToProject(Project mainProject, Project moduleSubproject, Project subProjectToAdd, String configurationContainerProperty, Dependency dependency, PomConfigurationType pomType) {
        if (!moduleSubproject.configurations.findByName(configurationContainerProperty)) {
            moduleSubproject.configurations.create(configurationContainerProperty)
        }
        // Add the project to the 'java' plugin configuration 'implementation' to be added automatically to the POM
        moduleSubproject.dependencies.add(DependencyUtils.IMPLEMENTATION, subProjectToAdd)

        // Store a Project dependency
        moduleSubproject.dependencies.add(configurationContainerProperty, subProjectToAdd)

        String dependencyName = "${dependency.group}:${dependency.name}"
        def pomContainer = PomConfigurationContainer.getPomContainer(mainProject, moduleSubproject, pomType)

        def subprojectDep = pomContainer.subprojectDependencies.get(dependencyName)

        // Store the Project dependency with the version declared in the 'build.gradle', used later to be replaced.
        if (!subprojectDep) {
            String currentDeclaredVersion = dependency.version
            subprojectDep = new PomProjectContainer(subProjectToAdd, currentDeclaredVersion, dependency)
            subprojectDep.isProjectDependency = true
            subprojectDep.setArtifactName(dependencyName)
            pomContainer.putSubproject(subprojectDep)
        }

        subprojectDep.recursivePublication = true
    }

    static Map<String, Project> generateProjectMap(List<Project> projectList) {
        Map<String, Project> projectMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)

        projectList.stream().forEach({
            String name = "${it.group}.${it.artifact}"
            projectMap.put(name, it)
        })

        return projectMap
    }

    /**
     * Throw on task called without command line parameter
     * Or project module not found
     * @param project
     * @param subprojectName
     * @return
     */
    static Project loadSubproject(Project project) {
        def moduleName = PublicationUtils.loadModuleName(project)
        def moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}:$moduleName")

        if (!moduleProject) {
            throw new IllegalArgumentException("The gradle project :$moduleName does not exists. \n" +
                    "Make sure that the project exists and contains the 'build.gradle' file, or run the 'createModuleBuild' task to generate it.")
        }
        return moduleProject
    }

}
