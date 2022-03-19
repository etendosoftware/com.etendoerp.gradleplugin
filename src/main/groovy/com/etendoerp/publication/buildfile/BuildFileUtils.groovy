package com.etendoerp.publication.buildfile

import com.etendoerp.jars.PathUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project

class BuildFileUtils {

    static final String BUNDLE_PROPERTY = "bundle"
    static final String BUNDLE_NOT_FOUND_ERROR = "* The passed bundle project does not exists:"

    static Project getBundleSubproject(Project project) {
        Project bundleProject = null
        String bundle = project.findProperty(BUNDLE_PROPERTY)
        // Check if the bundle subproject exists
        if (bundle) {
            bundleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}:$bundle")
            if (!bundleProject) {
                throw new IllegalArgumentException("${BUNDLE_NOT_FOUND_ERROR} '${bundle}'")
            }
        }
        return bundleProject
    }

    /**
     * Obtain the module 'javapackage' from a GIT url
     * @param repo
     */
    static getModuleJavaPackageFromGitRepo(String repo) {
        String[] splitURI = ((String) repo).split("/")
        return splitURI[1].substring(0, splitURI[1].size() - 4)
    }

    static void processUnloadedModulesList(Project project, BuildMetadata buildMetadata, List<String> unloadedModules, String type) {
        if (unloadedModules && !unloadedModules.isEmpty()) {
            unloadedModules.each {
                project.logger.warn("*****")
                project.logger.error("** ERROR: The module with '${type}' '${it}' was not found in the source modules. Parent module: ${buildMetadata.javaPackage}")
            }
        }
    }

    static String verifyModuleLocation(Project project, String moduleName) {
        String moduleLocation = PathUtils.createPath(
                project.rootDir.absolutePath,
                PublicationUtils.BASE_MODULE_DIR,
                moduleName
        )

        File location = project.file(moduleLocation)

        if(!location.exists()) {
            throw new IllegalArgumentException("The module '${moduleLocation}' does not exists.")
        }

        return location.absolutePath
    }

}
