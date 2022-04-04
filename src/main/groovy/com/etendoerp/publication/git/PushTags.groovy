package com.etendoerp.publication.git

import com.etendoerp.publication.PublicationLoader
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.configuration.PublicationConfiguration
import org.gradle.api.Project

import java.nio.file.Path
import java.nio.file.Paths

class PushTags {

    static final String PUSH_ALL_MODULES_PROPERTY = "pushAll"

    static load(Project project) {
        project.tasks.register(PublicationLoader.PUSH_GIT_TASK) {
            doLast {
                def modulesToUpdate = filterProjectsToPush(project)
                pushModulesAndTag(project, modulesToUpdate)
            }
        }
    }

    static void pushModulesAndTag(Project project, Map<String, String> modules) {
        File baseModuleFile = new File(project.rootDir, PublicationUtils.BASE_MODULE_DIR)

        Map<String, String> errorModules = [:]

        modules.each {
            String moduleName = it.key
            String moduleVersion = it.value
            String moduleNameFileLocation = "${baseModuleFile.absolutePath}${File.separator}${moduleName}"

            try {
                File gitIgnoreFile = new File(moduleNameFileLocation, ".gitignore")

                if (gitIgnoreFile && gitIgnoreFile.exists()) {
                    changeGitIgnore(gitIgnoreFile)
                }

                Path directory = Paths.get(moduleNameFileLocation)
                Git.gitStage(project, directory)
                Git.gitCommit(project, directory, "Updating module version")
                Git.gitPush(project, directory)

                // Tag
                Git.gitTag(project, directory, "v${moduleVersion}", "Tagging ${moduleVersion}")
                Git.gitPushTag(project, directory)
            } catch (IllegalAccessError e) {
                project.logger.error("* Error updating the git repository of the module '${moduleName}'")
                project.logger.error("* ERROR: ${e.getMessage()}")
                errorModules.put(moduleName, moduleVersion)
            }
        }

        errorModules.each {
            project.logger.error("* The module '${it.key}:${it.value}' has not been updated.")
        }

        if (errorModules && !errorModules.isEmpty()) {
            throw new IllegalArgumentException("* Error updating git modules repositories.")
        }
    }

    static void changeGitIgnore(File gitIgnoreFile) {
        String gitText = gitIgnoreFile.text
        gitText += "\n"
        gitText += "build/ \n"
        gitText += "/build/ \n"
        gitText += "etendo.artifact.properties \n"
        gitIgnoreFile.text = gitText
    }

    /**
     * Filter the projects with updated versions
     * @param project
     * @return
     */
    static Map<String, String> filterProjectsToPush(Project project) {
        def moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}")
        Map<String, String> modules = [:]

        boolean pushAll = project.findProperty(PUSH_ALL_MODULES_PROPERTY) ? true : false

        // Filter the projects
        moduleProject.subprojects.each {
            boolean isPublished = it.findProperty(PublicationConfiguration.PUBLISHED_FLAG)
            if (pushAll || isPublished) {
                modules.put(it.projectDir.name, it.version as String)
            }
        }
        return modules
    }

}
