package com.etendoerp.legacy.modules

import com.etendoerp.gradleutils.GradleUtils
import com.etendoerp.jars.modules.metadata.ModuleBuildMetadata
import com.etendoerp.jars.modules.metadata.ModuleDeployMetadata
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.bundling.Zip

class ModuleZipPublication {

    final static String PUBLICATION_NAME    = "ZipModule"
    final static String PUBLICATION_DESTINE = "MavenRepository"

    static void load(Project project) {

        project.publishing {
            publications {
                "$PUBLICATION_NAME"(MavenPublication){
                }
            }
        }

        /**
         * This task require command line parameter -Ppkg=<package name>.
         * Based on package name, gets build.gradle information and customize publication with that info.
         * First makes zip file, then publish to nexus repository.
        */
        project.tasks.register("publishZipConfig") {
            def zipTask = "generateModuleZip"
            dependsOn(zipTask)
            doLast {
                AbstractPublishToMaven publishTask = project.tasks.findByName("publish${PUBLICATION_NAME}PublicationTo${PUBLICATION_DESTINE}") as AbstractPublishToMaven

                def zipMetadata = new ModuleBuildMetadata(project, PublicationUtils.loadModuleName(project))
                zipMetadata.showModuleMetadata()

                // Cast the jar task
                Zip moduleZip = project.tasks.named(zipTask).get() as Zip
                def zipLocation = moduleZip.archiveFile.get()

                // Load the maven task with the necessary information to publish
                zipMetadata.loadMavenTask(publishTask, zipLocation)
            }
        }

        project.tasks.register("publishZip") {
            def tasks = [
                    "publishZipConfig",
                    "publish${PUBLICATION_NAME}PublicationTo${PUBLICATION_DESTINE}"
            ]
            GradleUtils.setTasksOrder(project, tasks)
            dependsOn(tasks)
        }

    }

}
