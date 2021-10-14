package com.etendoerp.legacy.dependencies

import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet

class ResolverDependencyUtils {

    static List<File> getJarFiles(Project project) {
        // Get all the base project configurations
        def baseProjectConfigurations = DependencyUtils.loadListOfConfigurations(project)

        // Get all the subproject configurations
        def subprojectConfigurations = DependencyUtils.getConfigurationsFromProject(project)

        // Add all the subproject configurations to the base project configuration
        baseProjectConfigurations.addAll(subprojectConfigurations)

        // The configuration container allows to filter equals dependencies with different versions
        // The container will contain the last version of a dependency
        def container = project.configurations.findByName(PublicationUtils.ETENDO_DEPENDENCY_CONTAINER)

        DependencySet containerSet = container.dependencies

        // Create a DependencySet with all the dependency from the base project and subprojects
        // The DependencySet can contain same dependencies with different versions
        DependencyUtils.loadDependenciesFromConfigurations(baseProjectConfigurations, containerSet)

        /**
         * Hack to load all the project and subproject dependencies to the 'root' project.
         * This allow defining dependencies in the 'build.gradle' file of submodules and being recognized
         * in all the project, simulating the legacy behavior.
         *
         * Only the major version of a dependency will be used, this is because the project sets all the
         * 'modules' in the main 'sourceSets', making the project and subprojects act like one project.
         *
         * PROS: If the project is considered like only one, there is not 'circular dependencies'.
         *
         * CONS: If two modules are using the same library with different version, the major one is taking
         * into account.
         *
         */
        containerSet.each {
            def dep = it
            project.dependencies {
                implementation(dep)
            }
        }

        // The collect method will perform the resolution of dependencies
        // And will return the major version of a dependency
        return container.collect()
    }

}
