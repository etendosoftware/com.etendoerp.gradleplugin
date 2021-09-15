package com.etendoerp.publication.buildfile

import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project

class ModuleBuildTemplateLoader {

    final static String BUILD_FILE = "build.gradle"

    final static String CREATE_MODULE_BUILD = "createModuleBuild"

    static void load(Project project) {

        /**
         * Task to create build.gradle from a module
         * This task require command line parameter -Ppkg=<package name> -Prepo=<Repository Name>
         * */
        project.task(CREATE_MODULE_BUILD) {
            doLast {

                String moduleName     = PublicationUtils.loadModuleName(project)
                String repositoryName = PublicationUtils.loadRepositoryName(project)

                def buildMetadata = new BuildMetadata(project, moduleName, repositoryName)

                def template = Thread.currentThread().getContextClassLoader().getResource("build.gradle.template")

                if (!template) {
                    throw new IllegalArgumentException("The ${BUILD_FILE} template does not exists.")
                }

                def tempBuildGradleFile = File.createTempFile("build.gradle","template")
                tempBuildGradleFile.text = template.text

                project.copy {
                    from(tempBuildGradleFile.absolutePath)
                    into(buildMetadata.moduleLocation)
                    rename { String filename ->
                        return BUILD_FILE
                    }
                    expand(buildMetadata.generatePropertiesMap())
                }
            }
        }

    }

}
