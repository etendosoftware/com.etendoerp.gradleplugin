package com.etendoerp.legacy.modules.expand

import com.etendoerp.jars.PathUtils
import com.etendoerp.legacy.utils.GithubUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Delete

class ExpandCustomModule {

    static void load(Project project) {

        project.task("cleanCustomExpandTmpDir", type: Delete) {
            delete {
                project.tasks.findByName("expandCustomModule").getTemporaryDir()
            }
        }

        project.tasks.register("expandCustomModule") {
            def extractDir = getTemporaryDir()
            finalizedBy(project.tasks.findByName("cleanCustomExpandTmpDir"))
            doLast {
                String moduleName = PublicationUtils.loadModuleName(project)
                GithubUtils.askCredentials(project)

                String moduleLocation = PathUtils.createPath(
                        project.rootDir.absolutePath,
                        PublicationUtils.BASE_MODULE_DIR,
                        moduleName
                )

                def moduleDepsConfig = project.configurations.findByName("moduleDeps")

                // Search module in the moduleDeps config
                def moduleNamePath = null;
                moduleDepsConfig.allDependencies.each {
                    def depModule = "${it.getGroup()}.${it.getName()}"
                    if (depModule == moduleName) {
                        moduleNamePath = File.separator + it.getGroup() + File.separator + it.getName() + File.separator
                    }
                }

                if (!moduleNamePath) {
                    throw new IllegalArgumentException("The custom module '${moduleName}' does not exists. \n" +
                            "Searched in the 'moduleDeps' configuration, make sure it is defined correctly.")
                }

                File customModuleJarFile = null
                moduleDepsConfig.getFiles().each {
                    if (it.absolutePath.contains(moduleNamePath)) {
                        customModuleJarFile = it
                        return
                    }
                }

                FileTree unzipCustomModule = project.zipTree(customModuleJarFile)

                // Copy the files to a temporary dir
                project.copy {
                    from (unzipCustomModule)
                    into (extractDir)
                }

                // Sync the files with the module directory
                project.ant.sync(todir:"${moduleLocation}") {
                    ant.fileset(dir: "${extractDir.getAbsolutePath()}/${moduleName}")
                }

                project.logger.info("The custom module was expanded successfully.")
            }
        }

    }

}
