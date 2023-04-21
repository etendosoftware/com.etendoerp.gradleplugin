package com.etendoerp.publication

import com.etendoerp.legacy.utils.GithubUtils
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven

class SubprojectJarsPublication {

    final static String DEV_URL = "https://repo.futit.cloud/repository/maven-snapshots/"
    final static String PROD_URL = "https://repo.futit.cloud/repository/maven-releases/"
    final static String GROUP = 'com.etendoerp.platform'

    static load(Project project) {
        def currentVersion
        project.publishing {
            publications {
                trlLib(MavenPublication) {
                    artifactId = 'trl-lib'
                    groupId = GROUP
                }
                wadLib(MavenPublication) {
                    artifactId = 'wad-lib'
                    groupId = GROUP
                }
                coreLib(MavenPublication) {
                    artifactId = 'core-lib'
                    groupId = GROUP
                }
            }
        }

        project.tasks.register('configTrlLib') {
            doLast {
                def publishTask = project.tasks.findByName("publishTrlLibPublicationToMavenRepository") as AbstractPublishToMaven
                publishTask.publication.artifact(project.file("$project.rootDir/src-trl/lib/openbravo-trl.jar"))
                publishTask.publication.version = project.version
                configRepo(project)
            }
        }

        project.tasks.register('configCoreLib') {
            doLast {
                def publishTask = project.tasks.findByName("publishCoreLibPublicationToMavenRepository") as AbstractPublishToMaven
                publishTask.publication.artifact(project.file("$project.rootDir/src-core/lib/openbravo-core.jar"))
                publishTask.publication.version = project.version
                configRepo(project)
            }
        }

        project.tasks.register('configWadLib') {
            doLast {
                def publishTask = project.tasks.findByName("publishWadLibPublicationToMavenRepository") as AbstractPublishToMaven
                publishTask.publication.artifact(project.file("$project.rootDir/src-wad/lib/openbravo-wad.jar"))
                publishTask.publication.version = project.version
                configRepo(project)
            }

        }

        project.tasks.register('publishTrlLibJar') {
            it.dependsOn(['trl.lib', 'configTrlLib', 'publishTrlLibPublicationToMavenRepository'])
        }
        project.configTrlLib.mustRunAfter("trl.lib")
        project.publishTrlLibPublicationToMavenRepository.mustRunAfter("configTrlLib")

        project.tasks.register('publishWadLibJar') {
            it.dependsOn(['wad.lib', 'configWadLib', 'publishWadLibPublicationToMavenRepository'])
        }
        project.configWadLib.mustRunAfter("wad.lib")
        project.publishWadLibPublicationToMavenRepository.mustRunAfter("configWadLib")

        project.tasks.register('publishCoreLibJar') {
            it.dependsOn(['core.lib', 'configCoreLib', 'publishCoreLibPublicationToMavenRepository'])
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
            GithubUtils.askCredentials(project)
            username project.ext.get("nexusUser")
            password project.ext.get("nexusPassword")
        }
    }
}
