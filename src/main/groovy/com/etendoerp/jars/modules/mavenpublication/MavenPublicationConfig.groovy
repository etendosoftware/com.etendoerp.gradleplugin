package com.etendoerp.jars.modules.mavenpublication

import com.etendoerp.jars.PathUtils
import com.etendoerp.jars.Utils
import com.etendoerp.legacy.utils.GithubUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip

class MavenPublicationConfig {

    static load(Project project) {

        String moduleName = project.findProperty(PublicationUtils.MODULE_NAME_PROP)

        // This code will be always executed
        // Prevent trying to configure the jar task of a unknown module
        // Because the module is obtained from the -Ppkg command line parameter
        if (!moduleName) {
            return
        }

        def moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}:$moduleName")
        if (!moduleProject) {
            return
        }

        moduleProject.tasks.register("mavenJarConfig") {
            dependsOn({project.tasks.findByName("compileJava")})
            doLast {
                // Get the module name
                moduleName = PublicationUtils.loadModuleName(project)

                project.logger.info("Starting module JAR configuration.")

                String moduleLocation = PathUtils.createPath(
                        project.rootDir.absolutePath,
                        PublicationUtils.BASE_MODULE_DIR,
                        moduleName
                )

                if (!project.file(moduleLocation).exists()) {
                    throw new IllegalArgumentException("The module $moduleLocation does not exist.")
                }

                String packagePath = PathUtils.fromModuleToPath(moduleName)
                String javaClassesLocation = PathUtils.createPath(project.buildDir.absolutePath, PublicationUtils.CLASSES)

                // Configure the task
                Task moduleJar = moduleProject.tasks.named("jar").get() as Jar

                // Obtains all the files from the 'src' folder, ignoring the '.java'.
                // This is to prevent applying different logic on every file found.
                String moduleSrcLocation = PathUtils.createPath(moduleLocation, PublicationUtils.SRC)
                moduleJar.from(moduleSrcLocation) {
                    exclude("**/*.java")
                }

                // Store all the files excluding the 'src' folder
                // in the 'META-INF/etendo/modules' dir.
                String destinationDir = PathUtils.createPath(PublicationUtils.META_INF, PublicationUtils.ETENDO, PublicationUtils.BASE_MODULE_DIR, moduleName)

                moduleJar.from(moduleLocation) {
                    include("*/**")
                    exclude(PublicationUtils.SRC)
                    exclude(PublicationUtils.EXCLUDED_FILES)
                    into(destinationDir)
                }

                /**
                 * This is used to include all the classes from 'build/classes' used by the BuildValidationHandler
                 *
                 * The default compileJava task will put the .class files in the build/etendo-classes dir.
                 */
                moduleJar.from(moduleLocation) {
                    include("build/classes/**")
                    into(destinationDir)
                }

            }
        }

        def moduleCapitalize = PublicationUtils.capitalizeModule(moduleName)
        def mavenTask = "publish${moduleCapitalize}PublicationTo${MavenPublicationLoader.PUBLICATION_DESTINE}"

        moduleProject.tasks.register("mavenPublishConfig") {
            def zipTask  = "generateModuleZip"
            dependsOn({
                project.tasks.findByName(zipTask)
            })
            doLast {
                def zip = project.tasks.findByName(zipTask) as Zip
                def zipFile = zip.archiveFile.get()

                AbstractPublishToMaven publishTask = moduleProject.tasks.findByName(mavenTask) as AbstractPublishToMaven
                publishTask.publication.artifact(zipFile)

                // Configure the credentials
                moduleProject.publishing.repositories.maven.credentials {
                    GithubUtils.askCredentials(project)
                    username project.ext.get("githubUser")
                    password project.ext.get("githubToken")
                }
            }
        }

        project.gradle.projectsEvaluated {
            moduleProject.java {
                withSourcesJar()
            }

            // JAR configuration
            def jarModuleTask = moduleProject.tasks.findByName("jar")
            if (!jarModuleTask) {
                project.logger.warn("WARNING: The subproject ${moduleProject} is missing the 'jar' task.")
                project.logger.warn("*** Make sure that the 'build.gradle' file is using the 'java' plugin.")
            }
            jarModuleTask?.dependsOn("mavenJarConfig")

            // Maven Publish configuration
            def mavenModuleTask = moduleProject.tasks.findByName(mavenTask)
            if (!mavenModuleTask) {
                project.logger.warn("WARNING: The subproject ${moduleProject} is missing the maven publiction task '${mavenTask}'.")
                project.logger.warn("*** Make sure that the 'build.gradle' file contains the MavenPublication '${moduleName}'.")
            }
            mavenModuleTask?.dependsOn("mavenPublishConfig")
        }
    }
}
