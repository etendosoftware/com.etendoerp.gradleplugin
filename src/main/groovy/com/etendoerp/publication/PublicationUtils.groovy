package com.etendoerp.publication

import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import com.etendoerp.legacy.utils.GithubUtils
import com.etendoerp.publication.buildfile.BuildMetadata
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.repositories.AbstractAuthenticationSupportedRepository
import org.gradle.api.logging.LogLevel

/**
 * Utility class for publication related operations
 */
class PublicationUtils {

    final static String BASE_REPOSITORY_URL = 'https://repo.futit.cloud/repository/'

    // Project property containing the module name
    // Ex: -Ppkg=com.greenterrace.hotelmanagement
    final static String MODULE_NAME_PROP = 'pkg'

    // Project property containing the repository name
    // Ex: -Prepo=etendo-test
    final static String REPOSITORY_NAME_PROP = 'repo'

    // Project property used has a flag to publish a ZIP version of a module
    // Ex: ./gradlew publishVersion -Ppkg=com.greenterrace.hotelmanagement -Pzip
    final static String PUBLISH_ZIP = 'zip'

    // Project property used has a flag to omit the dependency verification.
    // Used when there are circular dependencies
    // Ex: ./gradlew publishVersion -Ppkg=com.greenterrace.hotelmanagement -Pomit
    final static String OMIT_DEPENDENCY_VERIFICATION = 'omit'

    final static String BASE_MODULE_DIR = 'modules'

    final static String CONFIGURATION_NAME = 'implementation'

    final static String ETENDO_DEPENDENCY_CONTAINER = 'etendoDependencyContainer'
    final static String MODULE_DEPENDENCY_CONTAINER = 'moduleDependencyContainer'

    final static String BASE     = 'build'
    final static String CLASSES  = 'classes'
    final static String META_INF = 'META-INF'
    final static String ETENDO   = 'etendo'
    final static String JAR      = 'jar'
    final static String SRC      = 'src'
    final static String LIB      = 'libs'

    // Files to exclude from a module
    final static EXCLUDED_FILES = [
            'build/',
            'build/**',
            '.gradle/**',
            'gradle/**',
            'deploy.gradle',
            'target/**',
            '*.xml',
            '${EtendoArtifactMetadata.METADATA_FILE}'
    ]

    /**
     * Loads the module name passed by the command line as a parameter.
     * @param project The project
     * @return The module name
     */
    static String loadModuleName(Project project) {
        String moduleName = project.findProperty(PublicationUtils.MODULE_NAME_PROP)

        if (!moduleName) {
            throw new IllegalArgumentException("The command line parameter -P${MODULE_NAME_PROP}=<module name> is missing.")
        }
        return moduleName
    }

    /**
     * Loads the repository name passed by the command line as a parameter.
     * @param project The project
     * @return The repository name
     */
    static String loadRepositoryName(Project project) {
        String repositoryName = project.findProperty(REPOSITORY_NAME_PROP)

        if (!repositoryName) {
            throw new IllegalArgumentException("The command line parameter -P${REPOSITORY_NAME_PROP}=<repository name> is missing.")
        }
        return repositoryName
    }

    /**
     * Capitalizes the first letter of the module name.
     * @param moduleName The module name
     * @return The capitalized module name
     */
    static String capitalizeModule(String moduleName) {
        return moduleName.substring(0, 1).toUpperCase() + moduleName.substring(1)
    }

    /**
     * Configures all the repositories of a project with the inserted credentials,
     * only if the repository has no credentials already set.
     * @param baseProject The base project
     * @param projectToConfigure The project to configure
     */
    static void configureProjectRepositories(Project baseProject, Project projectToConfigure) {
        // Add all the module project repositories to the base repository
        baseProject.repositories.addAll(projectToConfigure.repositories)

        // Configure all the repositories credentials
        GithubUtils.askCredentials(baseProject)

        // Add all the base repositories from the base project to the module project
        // The repositories are used to resolve the graph of dependencies
        projectToConfigure.repositories.addAll(baseProject.repositories)

        // Configure credentials for repositories
        projectToConfigure.repositories.each {
            def repo = it as AbstractAuthenticationSupportedRepository
            def credentials = repo.getCredentials()
            if (!credentials.username && !credentials.password) {
                credentials.username = baseProject.findProperty('nexusUser')
                credentials.password = baseProject.findProperty('nexusPassword')
            }
        }
    }

    /**
     * Loads the module name from a subproject.
     * @param mainProject The main project
     * @param subProject The subproject
     * @param logLevel The log level
     * @return The optional module name
     */
    static Optional<String> loadModuleName(Project mainProject, Project subProject, logLevel = LogLevel.DEBUG) {
        String group = subProject.group as String
        String artifact = subProject.findProperty(BuildMetadata.ARTIFACT) as String

        if (!group) {
            mainProject.logger.log(logLevel, "* The subproject '${subProject}' does not contain the 'group' property. Make sure it is defined in the build.gradle (group = 'modulegroup')")
            return Optional.empty()
        }

        if (!artifact) {
            mainProject.logger.log(logLevel, "* The subproject '${subProject}' does not contain the 'artifact' property. Make sure it is defined in the build.gradle (ext.artifact = 'moduleartifact')")
            return Optional.empty()
        }

        String moduleName = "${group}.${artifact}"
        return Optional.of(moduleName)
    }
}
