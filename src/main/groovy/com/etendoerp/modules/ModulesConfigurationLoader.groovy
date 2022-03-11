package com.etendoerp.modules

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.jars.modules.mavenpublication.MavenPublicationConfig
import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.legacy.dependencies.ResolverDependencyUtils
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.configuration.PublicationConfiguration
import com.etendoerp.publication.taskloaders.PublicationTaskLoader
import com.etendoerp.publication.taskloaders.ZipTaskGenerator
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.file.UnionFileCollection
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.maven.MavenPublication

/**
 * This class configures all the module subprojects sourcesSets.
 * Sets the 'srcDir' to the 'src' folder of the module.
 * Configures the 'runtimeClasspath' and 'compileClasspath' using the root 'project' classpath.
 *
 * The 'project.sourceSets.main.output' uses the output of different submodules classes
 * This is usefully when there is a dependency between submodules
 * (This could be solved if the users sets in the 'build.gradle' of the module a dependency from other module
 *  Ex: implementation 'project(:modules:submodule).
 *
 *  Then will be not necessary use the 'output' of the root project, and when the module is going to be published
 *  the dependencies will be set automatically.
 * )
 *
 * The 'project.sourceSets.main.compileClasspath' uses the classpath set by dependencies
 * using 'implementation','compile',etc
 *
 * At the end the clause 'evaluationDependsOn' is added to evaluate each subproject before the root project.
 * This is done to obtain the Configurations And Dependencies from the subprojects when running the resolution conflicts. (ResolverDependencyLoader class)
 *
 */
class ModulesConfigurationLoader {

    final static String JAVA_SOURCES = "src"
    final static String ERROR_MISSING_PLUGIN = "Make sure that the 'build.gradle' file is using the 'java' plugin."

    static void load(Project project) {
        def moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}")

        if (moduleProject != null) {

            def extension = project.extensions.findByType(EtendoPluginExtension)

            moduleProject.subprojects.each {subproject ->
                subproject.pluginManager.apply("java")
                subproject.pluginManager.apply("maven-publish")

                // Create the configuration used to reference other subproject dependencies
                if (!subproject.configurations.findByName(PublicationConfiguration.SUBPROJECT_DEPENDENCIES_CONFIGURATION_CONTAINER)) {
                    subproject.configurations.create(PublicationConfiguration.SUBPROJECT_DEPENDENCIES_CONFIGURATION_CONTAINER)
                }

                subproject.afterEvaluate {
                    // Load the tasks related to the publication of the module subproject
                    PublicationTaskLoader.load(project, subproject)

                    // Throw error when a module subproject does not have the java plugin
                    if (!subproject.getPluginManager().hasPlugin("java")) {
                        throw new IllegalArgumentException("WARNING: The subproject ${subproject} is missing the 'java' plugin. \n" +
                                "*** ${ERROR_MISSING_PLUGIN}")
                    }

                    // Exclude the core dependency
                    def excludeCoreDependency = extension.excludeCoreDependencyFromSubprojectConfigurations
                    if (excludeCoreDependency) {
                        def subprojectConfigurations = DependencyUtils.loadListOfConfigurations(subproject)
                        subprojectConfigurations.each {
                            ResolverDependencyUtils.excludeCoreDependencies(project, it, false)
                        }
                    }

                    /**
                     * Override the default output for the .class files because the
                     * BuildValidationHandler considers it as a core class. (Search all classes in 'build/classes' for each module).
                     * The BuildValidationHandler tries to load the classes but
                     * if a class contains a library not included in the classpath, the 'update.database' task fails.
                     */
                    def output = subproject.file("${subproject.buildDir.absolutePath}/etendo-classes")
                    subproject.sourceSets.main.java.destinationDirectory.set(output)

                    subproject.sourceSets.main.java.srcDirs += JAVA_SOURCES
                    subproject.sourceSets.main.compileClasspath += project.sourceSets.main.output
                    subproject.sourceSets.main.runtimeClasspath += project.sourceSets.main.output
                    subproject.sourceSets.main.compileClasspath += project.sourceSets.main.compileClasspath
                    subproject.sourceSets.main.runtimeClasspath += project.sourceSets.main.runtimeClasspath

                }
                // Each subproject should be evaluated before the root project
                project.evaluationDependsOn(subproject.path)
            }
        }
    }

}
