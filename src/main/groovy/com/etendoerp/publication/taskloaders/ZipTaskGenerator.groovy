package com.etendoerp.publication.taskloaders

import com.etendoerp.jars.FileExtensions
import com.etendoerp.jars.PathUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Zip

/**
 * ZipTaskGenerator class for generating zip tasks
 */
class ZipTaskGenerator {

    final static String ZIP_CONFIG_TASK = 'generateModuleZipConfig'
    final static String ZIP_TASK = 'generateModuleZip'

    /**
     * Load method to load zip tasks
     * @param mainProject the main project
     * @param subProject the sub project
     */
    static void load(Project mainProject, Project subProject) {
        if (!subProject.tasks.findByName(ZIP_CONFIG_TASK)) {
            subProject.tasks.register(ZIP_CONFIG_TASK) {
                doLast {
                    // Get the module name
                    String moduleName = PublicationUtils.loadModuleName(mainProject, subProject).orElseThrow()
                    File moduleLocation = subProject.projectDir

                    // Configure the task
                    Task moduleZip = subProject.tasks.named(ZIP_TASK).get() as Zip

                    // Duplicate strategy 'EXCLUDE' to only copy one time the same file
                    moduleZip.duplicatesStrategy = DuplicatesStrategy.EXCLUDE

                    moduleZip.archiveFileName = "${moduleName}${FileExtensions.ZIP}"

                    def destinationDir = PathUtils.createPath(mainProject.buildDir.absolutePath, PublicationUtils.LIB)
                    moduleZip.destinationDirectory = mainProject.file(destinationDir)

                    def excludedFiles = PublicationUtils.EXCLUDED_FILES.findAll { !it.toLowerCase().contains('build') }

                    moduleZip.exclude(excludedFiles)
                    moduleZip.exclude('build/libs')
                    moduleZip.exclude('build/publications')
                    moduleZip.exclude('build/etendo-classes')
                    moduleZip.exclude('build/generated')
                    moduleZip.exclude('build/tmp')

                    // Verify if the build files needs to be changed
                    def parserResult = TaskLoaderUtils.parseFilesToTemporaryDir(mainProject, subProject)
                    if (parserResult && parserResult.isPresent()) {
                        moduleZip.from(parserResult.get())
                        moduleZip.into(moduleName)
                    }

                    moduleZip.from(moduleLocation)
                    moduleZip.into(moduleName)
                }
            }
        }

        if (!subProject.tasks.findByName(ZIP_TASK)) {
            subProject.tasks.register(ZIP_TASK, Zip) {
                dependsOn(ZIP_CONFIG_TASK)
                doLast {
                    mainProject.logger.info("The ZIP file '${archiveFileName.get()}' has been created in the '${destinationDirectory.get()}' directory.")
                }
            }
        }
    }

}