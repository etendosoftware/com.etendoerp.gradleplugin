package com.etendoerp.publication

import com.etendoerp.legacy.utils.NexusUtils
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven

class SubprojectJarsPublication {

    final static String DEV_URL = "https://repo.futit.cloud/repository/maven-snapshots/"
    final static String PROD_URL = "https://repo.futit.cloud/repository/maven-releases/"
    final static String GROUP = 'com.etendoerp.platform'

    static load(Project project) {
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


        project.tasks.register('publishTrlLibJar') {
            configTrlLib(project, getParamVersion(project))
            it.dependsOn(['trl.lib', 'publishTrlLibPublicationToMavenRepository'])
        }
        project.publishTrlLibPublicationToMavenRepository.mustRunAfter("trl.lib")

        project.tasks.register('publishWadLibJar') {
            configWadLib(project, getParamVersion(project))
            it.dependsOn(['wad.lib', 'publishWadLibPublicationToMavenRepository'])
        }
        project.publishWadLibPublicationToMavenRepository.mustRunAfter("wad.lib")

        project.tasks.register('publishCoreLibJar') {
            configCoreLib(project, getParamVersion(project))
            it.dependsOn(['core.lib', 'publishCoreLibPublicationToMavenRepository'])
        }
        project.publishCoreLibPublicationToMavenRepository.mustRunAfter("core.lib")
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

    static void configWadLib(project, version) {
        def publishTask = project.tasks.findByName("publishWadLibPublicationToMavenRepository") as AbstractPublishToMaven
        publishTask.publication.artifact(project.file("$project.rootDir/src-wad/lib/openbravo-wad.jar"))
        publishTask.publication.version= version
        configRepo(project)
    }
    static void configCoreLib(project, version) {
        def publishTask = project.tasks.findByName("publishCoreLibPublicationToMavenRepository") as AbstractPublishToMaven
        publishTask.publication.artifact(project.file("$project.rootDir/src-core/lib/openbravo-core.jar"))
        publishTask.publication.version= version
        configRepo(project)
    }
    static void configTrlLib(project, version) {
        def publishTask = project.tasks.findByName("publishTrlLibPublicationToMavenRepository") as AbstractPublishToMaven
        publishTask.publication.artifact(project.file("$project.rootDir/src-trl/lib/openbravo-trl.jar"))
        publishTask.publication.version= version
        configRepo(project)
    }

    static String getParamVersion(project){
        String paramVersion = project.findProperty("version")
        if (!paramVersion || paramVersion=="unspecified") {
            throw new IllegalArgumentException("The command line parameter -PVersion=<version> is missing.")
        }
        return paramVersion
    }
}
