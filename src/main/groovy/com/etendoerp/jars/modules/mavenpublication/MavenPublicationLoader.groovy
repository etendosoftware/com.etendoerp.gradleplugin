package com.etendoerp.jars.modules.mavenpublication

import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project

class MavenPublicationLoader {

    final static String PUBLICATION_DESTINE = "MavenRepository"

    static load(Project project) {

        String moduleName = project.findProperty(PublicationUtils.MODULE_NAME_PROP)

        project.tasks.register("publishMavenJar") {
            // Config phase, always executed
            if (moduleName) {
                def moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}:$moduleName")
                if (moduleProject) {
                    // Config task dependency on the generated maven task to publish
                    dependsOn({
                        moduleName = PublicationUtils.capitalizeModule(moduleName)
                        def mavenTask = "publish${moduleName}PublicationTo${PUBLICATION_DESTINE}"
                        return moduleProject.tasks.findByName(mavenTask)
                    })
                }
            }
            // Task called on demand
            doLast {
                // Throw on task called without command line parameter
                // Or project module not found
                moduleName = PublicationUtils.loadModuleName(project)
                def moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}:$moduleName")

                if (moduleProject == null) {
                    throw new IllegalArgumentException("The gradle project :$moduleName does not exists.")
                }
            }
        }

        // Config the subproject JAR task
        MavenPublicationConfig.load(project)

    }

}
