package com.etendoerp.publication.taskloaders

import com.etendoerp.gradleutils.GradleUtils
import com.etendoerp.legacy.utils.GithubUtils
import com.etendoerp.legacy.utils.NexusUtils
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.configuration.pom.PomConfigurationContainer
import org.gradle.api.Project
import org.gradle.api.publish.maven.internal.artifact.AbstractMavenArtifact
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.bundling.Zip

class MavenTaskGenerator {
    final static String MAVEN_CONFIG_TASK = "mavenConfigTask"
    final static String LOCAL_CONFIG_TASK = "localConfigTask"
    final static String POM_CONFIG_TASK   = "pomConfigTask"
    final static String METADATA_CONFIG_TASK = "metadataConfigTask"

    static final String POM_CONFIG_PROPERTY = "configureOnlyPom"

    static void load(Project mainProject, Project subProject) {
        def moduleName = PublicationUtils.loadModuleName(mainProject, subProject).orElseThrow()
        def moduleCapitalize = PublicationUtils.capitalizeModule(moduleName)
        def mavenTask = "publish${moduleCapitalize}PublicationTo${PublishTaskGenerator.PUBLICATION_MAVEN_DESTINE}"
        def localTask = "publish${moduleCapitalize}PublicationTo${PublishTaskGenerator.PUBLICATION_LOCAL_DESTINE}"
        def pomTask   = "generatePomFileFor${moduleCapitalize}Publication"
        def metadataTask= "generateMetadataFileFor${moduleCapitalize}Publication"

        // Configure local publication
        configurePublicationTask(mainProject, subProject, LOCAL_CONFIG_TASK, localTask)

        // Configure maven publication
        configurePublicationTask(mainProject, subProject, MAVEN_CONFIG_TASK, mavenTask)

        def configureOnlyPom = mainProject.findProperty(POM_CONFIG_PROPERTY)
        if (configureOnlyPom) {
            // This prevents uploading the gradle 'metadata' file
            subProject.tasks.withType(GenerateModuleMetadata) {
                enabled = false
            }

            // Configure POM file
            configurePomTask(mainProject, subProject, POM_CONFIG_TASK, pomTask, localTask, mavenTask)
            configureMetadataTask(mainProject, subProject, METADATA_CONFIG_TASK, metadataTask, localTask, mavenTask)
        }
    }

    static void configurePublicationTask(Project mainProject, Project subProject, String configTaskName, String publicationTaskName) {
        if (!subProject.tasks.findByName(configTaskName)) {
            subProject.tasks.register(configTaskName) {
                def zipTask  = ZipTaskGenerator.ZIP_TASK
                dependsOn({
                    subProject.tasks.findByName(zipTask)
                })
                doLast {
                    // Verify if the 'zip' artifact already exists in the publication.
                    AbstractPublishToMaven publishTask = subProject.tasks.findByName(publicationTaskName) as AbstractPublishToMaven
                    def zip = subProject.tasks.findByName(zipTask) as Zip
                    def zipFile = zip.archiveFile.get()

                    def zipArtifact = publishTask.publication.artifacts.stream().filter({
                        boolean isZipArtifact = false
                        if (it instanceof AbstractMavenArtifact) {
                            AbstractMavenArtifact mavenArtifact = it as AbstractMavenArtifact
                            return it.extension == "zip" && it.getFile().name.toLowerCase().contains(zipFile.getAsFile().name.toLowerCase())
                        }
                        return isZipArtifact
                    }).findAny()

                    if (!zipArtifact || !zipArtifact.isPresent()) {
                        publishTask.publication.artifact(zipFile)
                    }

                    // Configure the credentials
                    subProject.publishing.repositories.maven.credentials {
                        if (!subProject.publishing.repositories.maven.url.toString().contains("repo.futit.cloud")) {
                            GithubUtils.askCredentials(mainProject)
                            username mainProject.ext.get("githubUser")
                            password mainProject.ext.get("githubToken")
                        } else{
                            NexusUtils.askNexusCredentials(mainProject)
                            username mainProject.ext.get("nexusUser")
                            password mainProject.ext.get("nexusPassword")
                        }

                    }
                }
            }
        }

        // Maven Publish configuration
        def publicationTask = GradleUtils.getTaskByName(mainProject, subProject, publicationTaskName)
        if (!publicationTask) {
            mainProject.logger.warn("WARNING: The subproject ${subProject} is missing the maven publiction task '${publicationTaskName}'.")
            mainProject.logger.warn("*** Make sure that the 'build.gradle' file contains the MavenPublication '${publicationTaskName}'.")
        }
        publicationTask?.configure({
            dependsOn({configTaskName})
        })
    }

    static void configureMetadataTask(Project mainProject, Project subProject, String configTaskName, String metadataTaskName, String localTaskName, String mavenTaskName) {
        if (!subProject.tasks.findByName(configTaskName)) {
            subProject.tasks.register(configTaskName) {
                doLast {
                }
            }
        }
        // Config metadata
        def metadataTask = GradleUtils.getTaskByName(mainProject, subProject, metadataTaskName)
        if (!metadataTask) {
            mainProject.logger.warn("WARNING: The subproject ${subProject} is missing the maven POM task '${metadataTaskName}'.")
            mainProject.logger.warn("*** Make sure that the 'build.gradle' file contains the MavenPublication '${PublicationUtils.loadModuleName(mainProject, subProject).get()}'.")
        }
        metadataTask?.configure({
            dependsOn(configTaskName)
        })
    }

    static void configurePomTask(Project mainProject, Project subProject, String configTaskName, String pomTaskName, String localTaskName, String mavenTaskName) {
        if (!subProject.tasks.findByName(configTaskName)) {
            subProject.tasks.register(configTaskName) {
                doLast {
                    AbstractPublishToMaven localTask = subProject.tasks.findByName(localTaskName) as AbstractPublishToMaven
                    localTask.publication.version = subProject.version

                    AbstractPublishToMaven mavenTask = subProject.tasks.findByName(mavenTaskName) as AbstractPublishToMaven
                    mavenTask.publication.version = subProject.version

                    def pomTask = subProject.tasks.findByName(pomTaskName) as GenerateMavenPom
                    def pomContainer = subProject.findProperty(PomConfigurationContainer.POM_CONTAINER_PROPERTY) as PomConfigurationContainer

                    if (pomContainer) {
                        pomContainer.pomTask = pomTask
                        pomContainer.configurePom()
                    }
                }
            }
        }

        // Config pom
        def pomTask = GradleUtils.getTaskByName(mainProject, subProject, pomTaskName)
        if (!pomTask) {
            mainProject.logger.warn("WARNING: The subproject ${subProject} is missing the maven POM task '${pomTaskName}'.")
            mainProject.logger.warn("*** Make sure that the 'build.gradle' file contains the MavenPublication '${PublicationUtils.loadModuleName(mainProject, subProject).get()}'.")
        }
        pomTask?.configure({
            dependsOn(configTaskName)
        })
    }

}
