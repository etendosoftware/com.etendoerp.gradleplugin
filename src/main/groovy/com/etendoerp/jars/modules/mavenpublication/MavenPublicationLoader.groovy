package com.etendoerp.jars.modules.mavenpublication

import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project

class MavenPublicationLoader {

    final static String PUBLICATION_DESTINE = "MavenRepository"

    static load(Project project) {

        project.tasks.register("publishMavenJar") {
            // Passing a 'closure' to the dependsOn will delay the execution
            // to when the task is called.
            dependsOn({
                // Throw on task called without command line parameter
                // Or project module not found
                def moduleName = PublicationUtils.loadModuleName(project)
                def moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}:$moduleName")

                if (!moduleProject) {
                    throw new IllegalArgumentException("The gradle project :$moduleName does not exists. \n" +
                            "Make sure that the project exists and contains the 'build.gradle' file, or run the 'createModuleBuild' task to generate it.")
                }

                def capitalized   = PublicationUtils.capitalizeModule(moduleName)
                def mavenTaskName = "publish${capitalized}PublicationTo${PUBLICATION_DESTINE}"

                def mavenTask = moduleProject.tasks.findByName(mavenTaskName)
                if (!mavenTask) {
                    throw new IllegalArgumentException("The module ${moduleProject} is missing the maven publiction task '${mavenTaskName}'. \n" +
                            "Make sure that the 'build.gradle' file contains the MavenPublication '${moduleName}'.")
                }
                return mavenTask
            })
        }

        // Config the subproject JAR task
        MavenPublicationConfig.load(project)
    }
}
