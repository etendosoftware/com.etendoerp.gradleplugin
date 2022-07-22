package com.etendoerp.publication.buildfile

import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.buildfile.update.BuildMetadataUpdate
import org.gradle.api.Project

class ModuleBuildTemplateLoader {

    static final String BUILD_FILE = "build.gradle"
    static final String CREATE_MODULE_BUILD = "createModuleBuild"
    /**
     * Property used to create the 'build.gradle' file from all the modules.
     */
    static final String ALL_COMMAND_PROPERTY = "all"

    static void load(Project project) {
        BuildMetadataUpdate.load(project)

        /**
         * Task to create build.gradle from a module
         * This task require command line parameter -Ppkg=<package name> -Prepo=<Repository Name>
         * */
        project.task(CREATE_MODULE_BUILD) {
            doLast {
                String moduleName     = PublicationUtils.loadModuleName(project)
                String repositoryName = PublicationUtils.loadRepositoryName(project)

                def template = Thread.currentThread().getContextClassLoader().getResource("build.gradle.template")

                if (!template) {
                    throw new IllegalArgumentException("The ${BUILD_FILE} template does not exists.")
                }

                def tempBuildGradleFile = File.createTempFile("build.gradle","template")
                tempBuildGradleFile.text = template.text

                BuildMetadataContainer container = new BuildMetadataContainer(project, repositoryName, tempBuildGradleFile)
                container.loadSubprojectMetadata()
                if (moduleName == ALL_COMMAND_PROPERTY) {
                    container.createSubprojectsBuildFile()
                } else {
                    container.createCustomSubprojectBuildFile(moduleName)
                }
            }
        }

    }

}
