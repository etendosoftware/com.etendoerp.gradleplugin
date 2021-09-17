package com.etendoerp.jars.modules

import com.etendoerp.jars.PathUtils
import com.etendoerp.jars.Utils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Jar

class ModuleJarGenerator {

    final static String JAR = "jar"

    static void load(Project project) {
        project.tasks.register("generateModuleJarConfig") {

            doLast {
                // Get the module name
                String moduleName = PublicationUtils.loadModuleName(project)

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
                Task moduleJar = project.tasks.named("generateModuleJar").get() as Jar

                moduleJar.archiveFileName = "${moduleName}.$JAR"

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

        project.tasks.register("generateModuleJar", Jar) {
            dependsOn("generateModuleJarConfig")
            doLast {
                project.logger.info("The JAR file '${archiveFileName.get()}' has been created in the '${destinationDirectory.get()}' directory.")
            }
        }
    }

}
