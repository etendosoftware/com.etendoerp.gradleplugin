package com.etendoerp.publication

import org.gradle.api.Project

class PublicationLoader {

    static void load(Project project) {

        /**
         * This Task publish a version of a module.
         * The default publish will be a JAR version.
         * if the command line parameter -Pzip is passed, the version will be a ZIP.
         */
        project.tasks.register("publishVersion2") {
            def defaultPublishTask = "publishJar"

            if (project.hasProperty(PublicationUtils.PUBLISH_ZIP)) {
                defaultPublishTask = "publishZip"
            }

            dependsOn({project.tasks.named(defaultPublishTask)})
        }

    }

}
