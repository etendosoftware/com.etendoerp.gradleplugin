package com.etendoerp.jars.modules.mavenpublication

import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import com.etendoerp.legacy.dependencies.container.DependencyType
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project

class MavenPublicationLoader {

    final static String PUBLICATION_DESTINE = "MavenRepository"

    static load(Project project) {

        project.tasks.register("publishMavenJar") {
            // Passing a 'closure' to the dependsOn will delay the execution
            // to when the task is called.
            def moduleName = null
            def moduleProject = null
            dependsOn({
                // Throw on task called without command line parameter
                // Or project module not found
                moduleName = PublicationUtils.loadModuleName(project)
                moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}:$moduleName")

                if (!moduleProject) {
                    throw new IllegalArgumentException("The gradle project :$moduleName does not exists. \n" +
                            "Make sure that the project exists and contains the 'build.gradle' file, or run the 'createModuleBuild' task to generate it.")
                }

                def capitalized   = PublicationUtils.capitalizeModule(moduleName)
                def mavenTaskName = "publish${capitalized}PublicationTo${PUBLICATION_DESTINE}"

                def mavenTask = moduleProject.tasks.findByName(mavenTaskName)
                if (!mavenTask) {
                    throw new IllegalArgumentException("The module ${moduleProject} is missing the maven publiction task '${mavenTaskName}'. \n" +
                            "Make sure that the 'build.gradle' file contains the MavenPublication '${moduleName}'.")
                }
                return mavenTask
            })
            doLast {
                if (moduleName && moduleProject) {

                    def group = moduleProject.group as String
                    def name = moduleProject.artifact as String
                    def version = moduleProject.version as String

                    if (group && name && version) {
                        EtendoArtifactMetadata metadata = new EtendoArtifactMetadata(project, DependencyType.ETENDOZIPMODULE, group, name, version)
                        def location = "${project.rootDir}${File.separator}${PublicationUtils.BASE_MODULE_DIR}${File.separator}${moduleName}"
                        metadata.createMetadataFile(location)
                    }
                }
            }
        }

        // Config the subproject JAR task
        MavenPublicationConfig.load(project)
    }
}
