package com.etendoerp.legacy.modules

import com.etendoerp.jars.FileExtensions
import com.etendoerp.jars.PathUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Zip

class ModuleZipGenerator {

    static void load(Project project) {
        project.tasks.register('generateModuleZipConfig') {
            doLast {
                // Get the module name
                String moduleName = PublicationUtils.loadModuleName(project)

                String moduleLocation = PathUtils.createPath(
                        project.rootDir.absolutePath,
                        PublicationUtils.BASE_MODULE_DIR,
                        moduleName
                )

                if (!project.file(moduleLocation).exists()) {
                    throw new IllegalArgumentException('The module $moduleLocation does not exist.')
                }

                // Configure the task
                Task moduleZip = project.tasks.named('generateModuleZip').get() as Zip

                moduleZip.archiveFileName = "${moduleName}${FileExtensions.ZIP}"

                def destinationDir = PathUtils.createPath(project.buildDir.absolutePath, PublicationUtils.LIB)
                moduleZip.destinationDirectory = project.file(destinationDir)

                moduleZip.exclude(PublicationUtils.EXCLUDED_FILES)

                moduleZip.from(moduleLocation)
                moduleZip.into(moduleName)
            }
        }

        project.tasks.register('generateModuleZip', Zip) {
            dependsOn('generateModuleZipConfig')
            doLast {
                project.logger.info('The ZIP file ${archiveFileName.get()} has been created in the ${destinationDirectory.get()} directory.')
            }
        }
    }
}