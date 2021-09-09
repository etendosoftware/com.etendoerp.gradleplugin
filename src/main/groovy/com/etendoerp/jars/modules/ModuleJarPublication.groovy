package com.etendoerp.jars.modules

import com.etendoerp.gradleutils.GradleUtils
import com.etendoerp.legacy.utils.NexusUtils
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.bundling.Jar

class ModuleJarPublication {

    final static String PUBLICATION_DATA    = "deploy.gradle"
    final static String PUBLICATION_NAME    = "JarModule"
    final static String PUBLICATION_DESTINE = "MavenLocal"

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

                def jarMetadata = new ModuleMetadata(project, ModuleJarUtils.loadModuleName(project))
                jarMetadata.showModuleMetadata()

                publishTask.publication.groupId    = jarMetadata.group
                publishTask.publication.artifactId = jarMetadata.artifactId
                publishTask.publication.version    = jarMetadata.version

                // Cast the jar task
                Jar moduleJar = project.tasks.named(jarTask).get() as Jar

                def jarLocation = moduleJar.archiveFile.get()
                publishTask.publication.artifact(jarLocation)

                def dependencies = jarMetadata.createDependenciesNode()
                def repositories = jarMetadata.createRepositoriesNode()
                publishTask.publication.pom.withXml {
                    it.asNode().append(dependencies)
                    it.asNode().append(repositories)
                }

                if (jarMetadata.group != null) {
                    project.publishing.repositories.maven.url = jarMetadata.repository
                    project.publishing.repositories.maven.credentials {
                        NexusUtils.askNexusCredentials(project)
                        username project.ext.get("nexusUser")
                        password project.ext.get("nexusPassword")
                    }
                }
            }
        }

        project.tasks.register("publishJar") {
            def tasks = [
                    "publishJarConfig",
                    "publish${PUBLICATION_NAME}PublicationToMavenLocal"
            ]
            GradleUtils.setTasksOrder(project, tasks)
            dependsOn(tasks)
        }

    }

}
