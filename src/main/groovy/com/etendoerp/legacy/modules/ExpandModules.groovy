package com.etendoerp.legacy.modules

import com.etendoerp.jars.PathUtils
import com.etendoerp.legacy.utils.NexusUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Sync

class ExpandModules {

    static void load(Project project) {

        project.tasks.register("expandCustomModuleConfig") {
            doLast {
                String moduleName = PublicationUtils.loadModuleName(project)

                NexusUtils.askNexusCredentials(project)

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

                Task expandCustomTask = project.tasks.named("expandCustomModule").get() as Sync

                expandCustomTask.from(unzipCustomModule) {
                    include ("${moduleName}/**")
                }
                expandCustomTask.into(project.file(moduleLocation))
                expandCustomTask.eachFile {fcp ->
                    fcp.path = fcp.path.replaceFirst("^$moduleName", '')
                }
                expandCustomTask.includeEmptyDirs = false
            }
        }

        project.tasks.register("expandCustomModule", Sync) {
            dependsOn("expandCustomModuleConfig")
            doLast {
                project.logger.info("The custom module was expanded successfully.")
            }
        }

    }
}
