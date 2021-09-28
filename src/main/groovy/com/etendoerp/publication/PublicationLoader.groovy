package com.etendoerp.publication

import com.etendoerp.publication.buildfile.ModuleBuildTemplateLoader
import org.gradle.api.Project

class PublicationLoader {

    static void load(Project project) {

        ModuleBuildTemplateLoader.load(project)

        /**
         * This Task publish a version of a module.
         * The default publish will be a JAR version.
         * The Sources JAR and the ZIP file of the module is also published.
         */
        project.tasks.register("publishVersion") {
            def defaultPublishTask = "publishMavenJar"
            dependsOn({project.tasks.named(defaultPublishTask)})
        }

    }

}
