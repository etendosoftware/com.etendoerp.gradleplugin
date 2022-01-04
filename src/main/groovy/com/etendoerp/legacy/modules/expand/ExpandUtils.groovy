package com.etendoerp.legacy.modules.expand

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult

class ExpandUtils {

    static List<String> IGNORED_DEPENDENCIES = [
        "com.smf.classic.core:ob"
    ]

    static List<String> getIncomingDependencies(Project project, Configuration configuration) {
        List<String> incomingDependencies = []
        configuration.incoming.each {
            for (DependencyResult dependency: it.resolutionResult.allDependencies) {
                DefaultResolvedDependencyResult dependencyResult = dependency as DefaultResolvedDependencyResult
                def dependencyName = dependencyResult.getRequested().displayName
                dependencyResult.getRequested()
                if (isIgnoredDependency(dependencyName)) {
                    continue
                }
                project.logger.info("Incoming dependency: ${dependencyName}")
                incomingDependencies.add(dependencyName)
            }
        }
        return incomingDependencies
    }

    static boolean isIgnoredDependency(String dependency) {
        for (String ignoreDependency : IGNORED_DEPENDENCIES) {
            if (dependency.contains(ignoreDependency)) {
                return true
            }
        }
        return false
    }

    static String SOURCE_MODULES_CONTAINER = "sourceModulesContainer"
    static Map<String, File> getSourceModulesFiles(Project project) {
        Map<String, File> sourceModules = new HashMap<>()

        def moduleDepConfig = project.configurations.getByName("moduleDeps")
        def incomingDependencies = getIncomingDependencies(project, moduleDepConfig)

        int index = 0
        incomingDependencies.each {
            def sourceDependency = "${it}@zip"
            try {
                project.logger.info("Trying to resolve source module dependency: ${sourceDependency}")

                // Get module name
                def moduleName = getModuleName(it)

                index++
                def configName = "${SOURCE_MODULES_CONTAINER}$index"
                def config = project.configurations.create(configName)

                // Add module dependency
                project.dependencies.add(configName, sourceDependency)

                // Resolve dependency
                config.collect().each {
                    project.logger.info("Source module dependency resolved: ${it}")
                    sourceModules.put(moduleName, it)
                }

            } catch (Exception e) {
                project.logger.info("The dependency ${sourceDependency} could not be resolved.")
                project.logger.info(e.getMessage())
            }
        }
        return sourceModules
    }

    static String getModuleName(String dependency) {
        def parts = dependency.split(":")
        def group = parts[0]
        def name = parts[1]
        return "${group}.${name}"
    }

}
