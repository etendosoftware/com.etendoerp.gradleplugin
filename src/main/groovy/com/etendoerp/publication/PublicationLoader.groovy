package com.etendoerp.publication

import com.etendoerp.gradleutils.GradleUtils
import com.etendoerp.publication.buildfile.ModuleBuildTemplateLoader
import com.etendoerp.publication.git.GitLoader
import org.gradle.api.Project

class PublicationLoader {

    final static String PUBLISH_ALL_MODULES_TASK = "publishAll"
    final static String PUBLISH_VERSION_TASK     = "publishVersion"
    final static String PUBLISH_LOCAL_DUMMY_TASK = "publishToLocalDummy"
    final static String PUBLISH_MAVEN_DUMMY_TASK = "publishToMavenDummy"
    final static String PUSH_GIT_TASK  = "pushToGit"

    final static String PUSH_PROPERTY = "pushAndTag"

    static void load(Project project) {
        GitLoader.load(project)
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

        // The git push task must run after the maven task
        def pushTask = GradleUtils.getTaskByName(project, project, PUSH_GIT_TASK)
        pushTask?.configure({
            it.mustRunAfter(mavenTask.get())
        })

        def pushAndTag = project.findProperty(PUSH_PROPERTY) ? true : false

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
            if (pushAndTag) {
                dependsOn({
                    pushTask.get()
                })
            }
        }

        project.tasks.register(PUBLISH_ALL_MODULES_TASK) {
            dependsOn({
                return localTask.get()
            }, {
                return mavenTask.get()
            })
            if (pushAndTag) {
                dependsOn({
                    pushTask.get()
                })
            }
        }
    }
}
