package com.etendoerp.legacy.modules.expand

import com.etendoerp.core.CoreMetadata
import com.etendoerp.core.CoreType
import com.etendoerp.legacy.dependencies.ArtifactDependency
import com.etendoerp.legacy.utils.NexusUtils
import org.gradle.api.Project

class ExpandModules {

    static void load (Project project) {

        // TODO: Change the task name
        project.tasks.register("expandModulesRefactor") {
            def extractDir = getTemporaryDir()
            doLast {
                CoreMetadata coreMetadata = new CoreMetadata(project)

                if (coreMetadata.coreType == CoreType.UNDEFINED) {
                    throw new IllegalArgumentException("The Etendo core is undefined.")
                }

                NexusUtils.askNexusCredentials(project)

                if (!coreMetadata.supportJars) {
                    expandModulesOnlySources(project, coreMetadata)
                } else {
                    expandModulesMix(project, extractDir)
                }
            }
        }
    }

    // Expand only the source version of the modules
    static void expandModulesOnlySources(Project project, CoreMetadata coreMetadata) {
        // Add the core dependency
        def sourceModules = ExpandUtils.getSourceModulesFiles(project, coreMetadata)
        for (ArtifactDependency artifact in sourceModules) {
            artifact.extract()
        }
    }

    // TODO:
    static void expandModulesMix(Project project, File extractDir) {
        // Get all the dependencies defined by the moduleDeps config
        def moduleDepConfig = project.configurations.getByName("moduleDeps")

        // Perform resolution conflicts

        // Filter the Jar and Zip files

        // For each Zip file:
        // If is defined by the user (not transitive), delete the Jar version if exists in build/etendo and sync it in modules/

        // If is transitive (not defined by the user):
        // Prevent extracting if is in the Jar file list(above) or in the /build/etendo dir.

    }

}
