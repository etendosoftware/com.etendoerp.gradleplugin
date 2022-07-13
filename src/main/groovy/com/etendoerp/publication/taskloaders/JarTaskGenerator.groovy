package com.etendoerp.publication.taskloaders

import com.etendoerp.gradleutils.GradleUtils
import com.etendoerp.jars.PathUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Jar

class JarTaskGenerator {
    final static String JAR_CONFIG_TASK = "jarConfigTask"

    static void load(Project mainProject, Project subProject) {

        if (!subProject.tasks.findByName(JAR_CONFIG_TASK)) {
            subProject.tasks.register(JAR_CONFIG_TASK) {
                dependsOn({mainProject.tasks.findByName("compileJava")})
                doLast {
                    // Get the module name
                    String moduleName = PublicationUtils.loadModuleName(mainProject, subProject).orElseThrow()
                    mainProject.logger.info("Starting module JAR configuration.")
                    String moduleLocation = subProject.projectDir

                    // Configure the task
                    Task moduleJar = subProject.tasks.named("jar").get() as Jar

                    // Duplicate strategy 'EXCLUDE' to only copy one time the same file
                    moduleJar.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE)

                    // Obtains all the files from the 'src' folder, ignoring the '.java'.
                    // This is to prevent applying different logic on every file found.
                    String moduleSrcLocation = PathUtils.createPath(moduleLocation, PublicationUtils.SRC)
                    moduleJar.from(moduleSrcLocation) {
                        exclude("**/*.java")
                    }

                    // Store all the files excluding the 'src' folder
                    // in the 'META-INF/etendo/modules' dir.
                    String destinationDir = PathUtils.createPath(PublicationUtils.META_INF, PublicationUtils.ETENDO, PublicationUtils.BASE_MODULE_DIR, moduleName)

                    // Verify if the build files needs to be changed
                    def parserResult = TaskLoaderUtils.parseFilesToTemporaryDir(mainProject, subProject)
                    if (parserResult && parserResult.isPresent()) {
                        moduleJar.from(parserResult.get()) {
                            into(destinationDir)
                        }
                    }

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
        }

        subProject.java {
            withSourcesJar()
        }

        // JAR configuration
        def jarModuleTask = GradleUtils.getTaskByName(mainProject, subProject, "jar")// subProject.tasks.named("jar")
        if (!jarModuleTask) {
            mainProject.logger.warn("WARNING: The subproject ${subProject} is missing the 'jar' task.")
            mainProject.logger.warn("*** Make sure that the 'build.gradle' file is using the 'java' plugin.")
        }

        jarModuleTask?.configure({
            dependsOn({JAR_CONFIG_TASK})
        })
    }

}
