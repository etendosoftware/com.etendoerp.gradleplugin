package com.etendoerp.publication

import com.etendoerp.legacy.utils.NexusUtils
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven

class SubprojectJarsPublication {

    final static String DEV_URL = "https://repo.futit.cloud/repository/maven-snapshots/"
    final static String PROD_URL = "https://repo.futit.cloud/repository/maven-releases/"
    final static String GROUP = 'com.etendoerp.platform'
    final static String VERSION = '1.0.0'

    static load(Project project) {

        project.publishing {
            publications {
                trlLib(MavenPublication) {
                    artifactId = 'trl-lib'
                    groupId = GROUP
                    version = VERSION
                }
                wadLib(MavenPublication) {
                    artifactId = 'wad-lib'
                    groupId = GROUP
                    version = VERSION
                }
                coreLib(MavenPublication) {
                    artifactId = 'core-lib'
                    groupId = GROUP
                    version = VERSION
                }
            }
        }

        project.tasks.register('configTrlLib') {
            doLast {
                def publishTask = project.tasks.findByName("publishTrlLibPublicationToMavenRepository") as AbstractPublishToMaven
                publishTask.publication.artifact(project.file("$project.rootDir/src-trl/lib/openbravo-trl.jar"))
                configRepo(project)

            }
        }

        project.tasks.register('configWadLib') {
            doLast {
                def publishTask = project.tasks.findByName("publishWadLibPublicationToMavenRepository") as AbstractPublishToMaven
                publishTask.publication.artifact(project.file("$project.rootDir/src-wad/lib/openbravo-wad.jar"))
                configRepo(project)
            }

        }
        project.tasks.register('configCoreLib') {
            doLast {
                def publishTask = project.tasks.findByName("publishCoreLibPublicationToMavenRepository") as AbstractPublishToMaven
                publishTask.publication.artifact(project.file("$project.rootDir/src-core/lib/openbravo-core.jar"))
                configRepo(project)
            }

        }

        project.tasks.register('publishTrlLibJar') {
            dependsOn(['trl.lib', 'configTrlLib', 'publishTrlLibPublicationToMavenRepository'])
        }
        project.configTrlLib.mustRunAfter("trl.lib")
        project.publishTrlLibPublicationToMavenRepository.mustRunAfter("configTrlLib")

        project.tasks.register('publishWadLibJar') {
            it.dependsOn(['wad.lib', 'configWadLib', 'publishWadLibPublicationToMavenRepository'])
        }
        project.configWadLib.mustRunAfter("wad.lib")
        project.publishWadLibPublicationToMavenRepository.mustRunAfter("configWadLib")

        project.tasks.register('publishCoreLibJar') {
            dependsOn(['core.lib', 'configCoreLib', 'publishCoreLibPublicationToMavenRepository'])
        }
        project.configCoreLib.mustRunAfter("core.lib")
        project.publishCoreLibPublicationToMavenRepository.mustRunAfter("configCoreLib")
    }

    static void configRepo(Project project) {
        if (project.version.endsWith('-SNAPSHOT')) {
            project.publishing.repositories.maven.url = DEV_URL
        } else {
            project.publishing.repositories.maven.url = PROD_URL
        }
        project.publishing.repositories.maven.credentials {
            NexusUtils.askNexusCredentials(project)
            username project.ext.get("nexusUser")
            password project.ext.get("nexusPassword")
        }
    }
}
