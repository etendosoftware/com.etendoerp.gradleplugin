package com.etendoerp.jars.modules

import com.etendoerp.gradleutils.GradleUtils
import com.etendoerp.jars.modules.metadata.ModuleBuildMetadata
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.bundling.Jar

class ModuleJarPublication {

    final static String PUBLICATION_DATA    = "deploy.gradle"
    final static String PUBLICATION_NAME    = "JarModule"
    final static String PUBLICATION_DESTINE = "MavenRepository"

    static void load(Project project) {

        project.publishing {
            publications {
                "$PUBLICATION_NAME"(MavenPublication){
                }
            }
        }

        project.tasks.register("publishJarConfig") {
            def jarTask = "generateModuleJar"
            dependsOn(jarTask)
            doLast {
                AbstractPublishToMaven publishTask = project.tasks.findByName("publish${PUBLICATION_NAME}PublicationTo${PUBLICATION_DESTINE}") as AbstractPublishToMaven

                def jarMetadata = new ModuleBuildMetadata(project, PublicationUtils.loadModuleName(project))
                jarMetadata.showModuleMetadata()

                // Cast the jar task
                Jar moduleJar = project.tasks.named(jarTask).get() as Jar
                def jarLocation = moduleJar.archiveFile.get()

                // Load the maven task with the necessary information to publish
                jarMetadata.loadMavenTask(publishTask, jarLocation)
            }
        }

        project.tasks.register("publishJar") {
            def tasks = [
                    "publishJarConfig",
                    "publish${PUBLICATION_NAME}PublicationTo${PUBLICATION_DESTINE}"
            ]
            GradleUtils.setTasksOrder(project, tasks)
            dependsOn(tasks)
        }

    }

}
