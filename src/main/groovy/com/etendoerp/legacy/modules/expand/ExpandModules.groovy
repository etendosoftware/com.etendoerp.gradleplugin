package com.etendoerp.legacy.modules.expand

import com.etendoerp.jars.PathUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult

class ExpandModules {

    static void load (Project project) {
        project.tasks.register("expandModules2") {
            def extractDir = getTemporaryDir()

            doLast {
                // Get core version to apply different logic
                def coreVersion = 21

                if (coreVersion == 21) {
                    expandModulesOnlySources(project, extractDir)
                }

                if (coreVersion == 22) {

                }
            }
        }
    }

    // Expand only the source version of the modules
    static void expandModulesOnlySources(Project project, File extractDir) {
        def sourceModules = ExpandUtils.getSourceModulesFiles(project)
        project.logger.info("Source Modules: ${sourceModules}")

        for (element in sourceModules) {
            String moduleName = element.key
            File moduleFile = element.value

            // Clean tmp dir
            project.delete(extractDir)

            FileTree unzipModule = project.zipTree(moduleFile)

            // Copy the files to a temporary dir
            project.copy {
                from (unzipModule)
                into (extractDir)
            }

            // Sync the files with the module directory
            project.ant.sync(todir:"${PublicationUtils.BASE_MODULE_DIR}${File.separator}${moduleName}") {
                ant.fileset(dir: "${extractDir.getAbsolutePath()}${File.separator}${moduleName}")
            }

            // Clean tmp dir
            project.delete(extractDir)
        }

    }

}
