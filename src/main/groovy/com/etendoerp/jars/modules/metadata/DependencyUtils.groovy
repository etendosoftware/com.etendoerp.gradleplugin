package com.etendoerp.jars.modules.metadata

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet

class DependencyUtils {

    final static String IMPLEMENTATION     = "implementation"
    final static String COMPILE            = "compile"
    final static String COMPILE_ONLY       = "compileOnly"
    final static String COMPILE_CLASSPATH  = "compileClasspath"

    final static String RUNTIME_ONLY       = "runtimeOnly"
    final static String RUNTIME_CLASSPATH  = "runtimeClasspath"

    final static List<String> validConfigurations = [
            IMPLEMENTATION    ,
            COMPILE           ,
            COMPILE_ONLY      ,
            COMPILE_CLASSPATH ,
            RUNTIME_ONLY      ,
            RUNTIME_CLASSPATH
    ]


    static List<Configuration> loadListOfConfigurations(Project project) {
        List<Configuration> list = new ArrayList()
        def configurationContainer = project.configurations
        validConfigurations.each {
            def configuration = configurationContainer.findByName(it)
            if (configuration != null) {
                list.add(configuration)
            }
        }
        return list
    }

    static DependencySet loadDependenciesFromConfigurations(List<Configuration> configurations) {
        DependencySet set = null
        configurations.each {
            // Continue on null configuration
            if (it == null) {
                return
            }
            // Initialize dependencies list
            if (set == null) {
                set = it.dependencies
                return
            }
            set.addAll(it.dependencies.toList())
        }
        return set
    }

}
