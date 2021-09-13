package com.etendoerp.publication

import org.gradle.api.Project
import org.gradle.api.internal.artifacts.repositories.AbstractAuthenticationSupportedRepository

class PublicationUtils {

    // Project property containing the module name
    // Ex: -Ppkg=com.greenterrace.hotelmanagement
    static final String MODULE_NAME_PROP = "mod"

    // Project property used has a flag to publish a ZIP version of a module
    // Ex: ./gradlew publishVersion -Ppkg=com.greenterrace.hotelmanagement -Pzip
    final static String PUBLISH_ZIP = "zip"

    final static String BASE_MODULE_DIR = "modules"

    final static String BASE     = "build"
    final static String CLASSES  = "classes"
    final static String META_INF = "META-INF"
    final static String ETENDO   = "etendo"
    final static String JAR      = "jar"
    final static String SRC      = "src"
    final static String LIB      = "libs"

    // Files to exclude from a module
    final static EXCLUDED_FILES = [
            ".gradle/**",
            "gradle/**",
            "deploy.gradle",
            "target/**",
            "*.xml"
    ]

    /**
     * Loads the module name passed by the command line as a parameter.
     * @param project
     * @return
     */
    static String loadModuleName(Project project) {
        String moduleName = project.findProperty(PublicationUtils.MODULE_NAME_PROP)

        if (moduleName == null || moduleName.isBlank()) {
            throw new IllegalArgumentException("The command line parameter -P${MODULE_NAME_PROP}=<module name> is missing.")
        }
        return moduleName
    }

    /**
     * Configures all the repositories of a project with the inserted credentials,
     * only if the repository has no credentials already set.
     * @param baseProject
     * @param projectToConfigure
     */
    static void configureProjectRepositories(Project baseProject, Project projectToConfigure) {

        projectToConfigure.repositories.each {
            def repo = it as AbstractAuthenticationSupportedRepository
            def credentials = repo.getCredentials()

            if (credentials.username == null || credentials.username.isBlank()
                    || credentials.password == null || credentials.password.isBlank()) {

                repo.credentials({
                    it.username = baseProject.ext.get("nexusUser")
                    it.password =  baseProject.ext.get("nexusPassword")
                })
            }
        }

    }

}
