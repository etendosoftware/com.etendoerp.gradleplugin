package com.etendoerp.publication

import com.etendoerp.publication.buildfile.ModuleBuildTemplateLoader
import com.etendoerp.publication.taskloaders.PublicationTaskLoader
import com.etendoerp.publication.taskloaders.PublishTaskGenerator
import org.gradle.api.Project

class PublicationLoader {

    final static String PUBLISH_ALL_MODULES_TASK = "publishAll"
    final static String PUBLISH_VERSION_TASK     = "publishVersion"
    final static String PUBLISH_LOCAL_DUMMY_TASK = "publishToLocalDummy"
    final static String PUBLISH_MAVEN_DUMMY_TASK = "publishToMavenDummy"

    static void load(Project project) {
        SubprojectJarsPublication.load(project)
        ModuleBuildTemplateLoader.load(project)

        def localTask = project.tasks.register(PUBLISH_LOCAL_DUMMY_TASK) {
            doLast {
                project.logger.info("* PublishToLocal tasks executed")
            }
        }
        def mavenTask = project.tasks.register(PUBLISH_MAVEN_DUMMY_TASK) {
            doLast {
                project.logger.info("* PublishToMaven tasks executed.")
            }
        }

        // The maven task must run after the local task.
        mavenTask.get().mustRunAfter(localTask.get())

        /**
         * This Task publish a version of a module.
         * The default publish will be a JAR version.
         * The Sources JAR and the ZIP file of the module is also published.
         */
        project.tasks.register(PUBLISH_VERSION_TASK) {
            dependsOn({
                return localTask.get()
            }, {
                return mavenTask.get()
            })
        }

        project.tasks.register(PUBLISH_ALL_MODULES_TASK) {
            dependsOn({
                return localTask.get()
            }, {
                return mavenTask.get()
            })
        }
    }
}
