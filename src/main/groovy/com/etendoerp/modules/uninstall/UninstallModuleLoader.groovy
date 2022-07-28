package com.etendoerp.modules.uninstall

import com.etendoerp.modules.ModuleUtils
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.configuration.PublicationConfigurationUtils
import org.gradle.api.Project

class UninstallModuleLoader {

    public static final String UNINSTALL_MODULE_TASK = "uninstallModule"

    static void load(Project project) {

        project.tasks.register(UNINSTALL_MODULE_TASK) {
            doLast {
                String moduleName = PublicationUtils.loadModuleName(project)

                // Load module subprojects
                def baseModuleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}")

                if (!baseModuleProject) {
                    throw new IllegalStateException("* The project :${PublicationUtils.BASE_MODULE_DIR} does not exists.")
                }

                // Filter the valid Etendo modules.
                def moduleSubprojects = PublicationConfigurationUtils.generateProjectMap(baseModuleProject.subprojects.findAll({
                    PublicationUtils.loadModuleName(project, it).isPresent()
                }).collect())

                def moduleToUninstall = moduleSubprojects.get(moduleName)

                if (!moduleToUninstall) {
                    throw new IllegalArgumentException("The Etendo module '${moduleName}' does not exists or the build.gradle is not properly configured.")
                }

                // Collect all the subproject dependencies to uninstall
                def modulesToUninstallMap = ModuleUtils.collectSourceModulesToUninstall(project, moduleToUninstall, moduleSubprojects)

                // Display menu confirmation
                if (ModuleUtils.shouldUninstallModulesMenu(project, moduleToUninstall, modulesToUninstallMap)) {
                    ModuleUtils.deleteModules(project, modulesToUninstallMap)
                }

            }
        }
    }
}
