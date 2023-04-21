package com.etendoerp.legacy.modules.expand

import com.etendoerp.core.CoreMetadata
import com.etendoerp.core.CoreType
import com.etendoerp.legacy.utils.GithubUtils
import org.gradle.api.Project

class ExpandModules {

    static void load (Project project) {

        project.tasks.register("expandModules") {
            doLast {
                CoreMetadata coreMetadata = new CoreMetadata(project)

                if (coreMetadata.coreType == CoreType.UNDEFINED) {
                    throw new IllegalArgumentException("The Etendo core is undefined.")
                }

                GithubUtils.askCredentials(project)
                def moduleDepsConfig = project.configurations.getByName("moduleDeps")

                project.logger.info("*****************************************************")
                project.logger.info("* Starting expanding modules.")
                project.logger.info("*****************************************************")

                def sourceFiles = ExpandUtils.getSourceModulesFiles(project, moduleDepsConfig, coreMetadata)
                ExpandUtils.expandModulesOnlySources(project, coreMetadata, moduleDepsConfig, sourceFiles)
            }
        }
    }

}
