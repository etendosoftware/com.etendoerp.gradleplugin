package com.etendoerp.modules

import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project

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
 */
class ModulesConfigurationLoader {

    final static String JAVA_SOURCES = "src"

    static void load(Project project) {
        def moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}")

        moduleProject.subprojects.each {subproject ->
            subproject.afterEvaluate {
                subproject.sourceSets.main.java.srcDirs += JAVA_SOURCES
                subproject.sourceSets.main.compileClasspath += project.sourceSets.main.output
                subproject.sourceSets.main.runtimeClasspath += project.sourceSets.main.output
                subproject.sourceSets.main.compileClasspath += project.sourceSets.main.compileClasspath
                subproject.sourceSets.main.runtimeClasspath += project.sourceSets.main.runtimeClasspath
            }
        }
    }

}
