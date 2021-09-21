package com.etendoerp.jars.modules.mavenpublication

import com.etendoerp.jars.PathUtils
import com.etendoerp.jars.Utils
import com.etendoerp.legacy.utils.NexusUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar

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

                // Obtains all the .class files
                moduleJar.from(javaClassesLocation) {
                    include("$packagePath/**/*.class")
                    exclude(PathUtils.fromPackageToPathClass(Utils.loadGeneratedEntitiesFile(project)))
                }

                // Obtains all the files from the 'src' folder, ignoring the '.java'.
                // This is to prevent applying different logic on every file found.
                String moduleSrcLocation = PathUtils.createPath(moduleLocation, PublicationUtils.SRC)
                moduleJar.from(moduleSrcLocation) {
                    exclude("**/*.java")
                }

                // Store all the files excluding the 'src' folder
                // in the 'META-INF/etendo' dir.
                String destinationDir = PathUtils.createPath(PublicationUtils.META_INF, PublicationUtils.ETENDO, moduleName)

                moduleJar.from(moduleLocation) {
                    include("*/**")
                    exclude(PublicationUtils.SRC)
                    exclude(PublicationUtils.EXCLUDED_FILES)
                    into(destinationDir)
                }
            }
        }

        moduleProject.tasks.register("mavenSourcesJarConfig") {
            doLast {
                moduleName = PublicationUtils.loadModuleName(project)

                project.logger.info("Starting module Sources JAR configuration.")

                String moduleLocation = PathUtils.createPath(
                        project.rootDir.absolutePath,
                        PublicationUtils.BASE_MODULE_DIR,
                        moduleName
                )

                if (!project.file(moduleLocation).exists()) {
                    throw new IllegalArgumentException("The module $moduleLocation does not exist.")
                }

                // Configure the task
                Task moduleJar = moduleProject.tasks.named("sourcesJar").get() as Jar

                String moduleSrcLocation = PathUtils.createPath(moduleLocation, PublicationUtils.SRC)
                moduleJar.from(moduleSrcLocation) {
                    include("**/*.java")
                }
            }
        }

        def moduleCapitalize = PublicationUtils.capitalizeModule(moduleName)
        def mavenTask = "publish${moduleCapitalize}PublicationTo${MavenPublicationLoader.PUBLICATION_DESTINE}"

        moduleProject.tasks.register("mavenPublishConfig") {
            doLast {
                // Configure the credentials
                moduleProject.publishing.repositories.maven.credentials {
                    NexusUtils.askNexusCredentials(project)
                    username project.ext.get("nexusUser")
                    password project.ext.get("nexusPassword")
                }
            }
        }

        project.gradle.projectsEvaluated {
            moduleProject.java {
                withSourcesJar()
            }

            // Sources JAR configuration
            moduleProject.tasks.findByName("sourcesJar").dependsOn("mavenSourcesJarConfig")

            // JAR configuration
            moduleProject.tasks.findByName("jar").dependsOn("mavenJarConfig")

            // Maven Publish configuration
            moduleProject.tasks.findByName(mavenTask).dependsOn("mavenPublishConfig")
        }
    }
}
