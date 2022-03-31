package com.etendoerp.jars.modules.metadata

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency

class DependencyUtils {

    final static String IMPLEMENTATION     = "implementation"
    final static String COMPILE            = "compile"
    final static String COMPILE_ONLY       = "compileOnly"
    final static String COMPILE_CLASSPATH  = "compileClasspath"

    final static String RUNTIME_ONLY       = "runtimeOnly"
    final static String RUNTIME_CLASSPATH  = "runtimeClasspath"

    final static List<String> VALID_CONFIGURATIONS = [
            IMPLEMENTATION    ,
            COMPILE           ,
            COMPILE_ONLY      ,
            COMPILE_CLASSPATH ,
            RUNTIME_ONLY      ,
            RUNTIME_CLASSPATH
    ]

    final static List<String> FILTER_CONFIGURATIONS = [
            IMPLEMENTATION,
            COMPILE
    ]

    /**
     * Loops over all the subprojects obtaining the configurations.
     * @param project
     * @return
     */
    static List<Configuration> getConfigurationsFromProject(Project project) {
        List<Configuration> allProjectConfigurations = new ArrayList<>()
        project.subprojects.each {
            def configs = loadListOfConfigurations(it)
            allProjectConfigurations.addAll(configs)
        }
        return allProjectConfigurations
    }


    /**
     * Gets the configurations of a Project passed has a parameter.
     * Only the configuration in the configToSearch list are returned.
     *
     * @param project
     * @param configToSearch
     * @return
     */
    static List<Configuration> loadListOfConfigurations(Project project, List<String> configToSearch = VALID_CONFIGURATIONS) {
        List<Configuration> list = new ArrayList()
        def configurationContainer = project.configurations
        configToSearch.each {
            def configuration = configurationContainer.findByName(it)
            if (configuration != null) {
                list.add(configuration)
            }
        }
        return list
    }

    static DependencySet loadDependenciesFromConfigurations(List<Configuration> configurations, DependencySet setToLoad, boolean onlyExternalDependencies = true) {
        DependencySet set = setToLoad
        configurations.each {
            // Continue on null configuration
            if (it == null) {
                return
            }
            it.allDependencies.each {
                // Used to prevent adding dependencies related to /lib/runtime
                // when needs to be load to the ANT classpath
                if (onlyExternalDependencies) {
                    if (it instanceof DefaultExternalModuleDependency) {
                        set.add(it)
                    }
                } else {
                    set.add(it)
                }
            }
        }
        return set
    }

    static int removeDependencyFromSubproject(Project mainProject, Project subProject, String dependencyGroup, String dependencyName, List<String> configurations=VALID_CONFIGURATIONS) {
        try {
            int removed = 0
            List<Configuration> configList = loadListOfConfigurations(subProject)
            for (Configuration configuration : configList) {
                def removedDep = configuration.dependencies.removeIf({
                    it.group == dependencyGroup && it.name == dependencyName
                })
                if (removedDep) {
                    removed ++
                }
            }
            return removed
        } catch (Exception e) {
            mainProject.logger.warn("* Error removing the dependencies of the '${subProject}'.")
            mainProject.logger.warn("* Error: ${e.getMessage()}")
            return 0
        }
    }

}
