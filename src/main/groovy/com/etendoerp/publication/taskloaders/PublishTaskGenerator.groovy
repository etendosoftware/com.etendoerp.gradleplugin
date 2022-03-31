package com.etendoerp.publication.taskloaders

import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import com.etendoerp.legacy.dependencies.container.DependencyType
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.configuration.pom.PomConfigurationContainer
import org.gradle.api.Project

class PublishTaskGenerator {

    final static String PUBLICATION_LOCAL_DESTINE = "MavenLocal"
    final static String PUBLICATION_MAVEN_DESTINE = "MavenRepository"

    final static String PUBLISH_MAVEN_TASK = "publishMavenJarTask"
    final static String PUBLISH_LOCAL_TASK = "publishLocalJarTask"

    static void load(Project mainProject, Project subProject) {
        // Load subproject local task publication
        createPublicationTasks(mainProject, subProject, PUBLISH_LOCAL_TASK, PUBLICATION_LOCAL_DESTINE)

        // Load subproject maven task publication
        createPublicationTasks(mainProject, subProject, PUBLISH_MAVEN_TASK, PUBLICATION_MAVEN_DESTINE)
    }

    static void createPublicationTasks(Project mainProject, Project subProject, String taskName, String destine) {
        def moduleName  = PublicationUtils.loadModuleName(mainProject, subProject).orElseThrow()
        def capitalized = PublicationUtils.capitalizeModule(moduleName)

        if (!subProject.tasks.findByName(taskName)) {
            subProject.tasks.register(taskName) {
                dependsOn({
                    def mavenTaskName = "publish${capitalized}PublicationTo${destine}"
                    def mavenTask = subProject.tasks.findByName(mavenTaskName)
                    if (!mavenTask) {
                        throw new IllegalArgumentException("The module ${subProject} is missing the maven publiction task '${mavenTaskName}'. \n" +
                                "Make sure that the 'build.gradle' file contains the MavenPublication '${moduleName}'.")
                    }
                    return mavenTask
                })
                doLast {
                    if (destine == PUBLICATION_MAVEN_DESTINE) {
                        createMetadataFile(mainProject, subProject)

                        // If the tasks is the publish to maven. copy the updated files to the module
                        def filesAlreadyParsed = subProject.findProperty(PomConfigurationContainer.PARSED_FILES_FLAG)
                        if (filesAlreadyParsed) {
                            copyParsedFilesToSubproject(mainProject, subProject)
                        }
                    }
                }
            }
        }
    }

    static void createMetadataFile(Project mainProject, Project subProject) {
        def group = subProject.group as String
        def name = subProject.artifact as String
        def version = subProject.version as String

        if (group && name && version) {
            EtendoArtifactMetadata metadata = new EtendoArtifactMetadata(mainProject, DependencyType.ETENDOZIPMODULE, group, name, version)
            def location = subProject.projectDir.absolutePath
            metadata.createMetadataFile(location)
        }
    }

    /**
     *
     * @param mainProject
     * @param subProject
     */
    static void copyParsedFilesToSubproject(Project mainProject, Project subProject) {
        def dummyTask = subProject.tasks.findByName(TaskLoaderUtils.DUMMY_TASK_TEMPORARY_DIR)
        if (dummyTask) {
            File temporaryDir = dummyTask.getTemporaryDir()
            mainProject.logger.info("* Copying parsed files to the project ${subProject}")
            mainProject.logger.info("* Files: ${temporaryDir.listFiles()}")
            subProject.copy {
                from(temporaryDir)
                into(subProject.projectDir)
            }
        }
    }

}
