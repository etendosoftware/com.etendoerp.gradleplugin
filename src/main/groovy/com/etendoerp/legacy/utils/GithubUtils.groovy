package com.etendoerp.legacy.utils

import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.component.Artifact
import org.gradle.api.internal.tasks.userinput.NonInteractiveUserInputHandler
import org.gradle.api.internal.tasks.userinput.UserInputHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository

class GithubUtils {

    /**
     * Tries to get user credentials from the project extra properties.
     * If the credentials are not found, searches the properties defined in the gradle.properties file or System properties.
     * A user can set his credentials or System properties in the gradle.properties file adding the lines:
     * githubUser=
     * githubToken=
     *
     * Or define System properties
     *
     * systemProp.githubUser=sysUser
     * systemProp.githubToken=sysPass
     *
     * Once the credentials are found, it sets its on the Project extra properties
     *
     */

    static String FUTIT_REPO_HOST = "repo.futit.cloud"

    static askCredentials(Project project) {

        def (githubUser, githubToken) = getCredentials(project)

        if (!githubUser || !githubToken) {
            def input = project.getServices().get(UserInputHandler.class)
            githubUser = project.getServices().get(UserInputHandler.class).askQuestion("GitHub user", "")
            githubToken = project.getServices().get(UserInputHandler.class).askQuestion("GitHub Token", "")
            if (!(input instanceof NonInteractiveUserInputHandler)) {
                // Do not send prompt when using an non interactive console.
                // Avoids a failure when refreshing gradle projects that use this function, in IntelliJ or other IDEs
                input.sendPrompt("\033[F\r" + "GitHub Token (default: ): **************************************** \n")
            }
        }

        if (githubUser && githubToken) {
            project.ext.set("githubUser", githubUser)
            project.ext.set("githubToken", githubToken)
        }

        configureRepositories(project, githubUser as String, githubToken as String)

        project.repositories {
            maven {
                url "https://repo.futit.cloud/repository/maven-releases"

            }
            maven {
                url "https://repo.futit.cloud/repository/maven-public-releases"
            }
        }
    }

    static def getCredentials(Project project) {
        def githubUser = ""
        def githubToken = ""

        if (project.findProperty("githubUser") != null && project.findProperty("githubToken") != null) {
            githubUser = project.ext.get("githubUser")
            githubToken = project.ext.get("githubToken")
        } else if (System.getProperty("githubUser") != null && System.getProperty("githubToken") != null) {
            githubUser = System.getProperty("githubUser")
            githubToken = System.getProperty("githubToken")
        } else if (project.hasProperty("githubUser") && project.hasProperty("githubToken")) {
            githubUser = project.property("githubUser")
            githubToken = project.property("githubToken")
        }

        return [githubUser, githubToken]
    }

    static void configureRepositories(Project project) {
        def (username, password) = getCredentials(project)
        configureRepositories(project, username as String, password as String)
    }

    /**
     * Adds a repository to the project only if does not already exists.
     * @param project
     * @param repository
     */
    static void addRepoToProject(Project project, ArtifactRepository repository) {
        String repoUrl = repository.properties.get("url").toString()

        ArtifactRepository projectRepo = project.repositories.find({
            it.properties.get("url")?.toString()?.equalsIgnoreCase(repoUrl)
        })

        // The project does not contain the repository
        if (!projectRepo) {
            def repoCredentials = repository["credentials"] as PasswordCredentials
            project.repositories {
                maven {
                    url "${repoUrl}"
                    if (repoCredentials.username && repoCredentials.password) {
                        credentials {
                            username = repoCredentials.username
                            password = repoCredentials.password
                        }
                    }
                }
            }
        }
    }

    /**
     * Configure all project and subproject repositories with the System credentials.
     * The credentials could be passed by the command line parameters '-DgithubUser=user -DgithubToken=password'
     */
    static void configureRepositories(Project project, String usernameCredential, String passwordCredential) {

        project.logger.info("Starting project repositories configuration.")

        /**
         * Gets all the subproject repositories and set its in the root project.
         * This is used to resolved all subproject dependencies.
         */
        project.subprojects.each {
            it.repositories.each { repo ->
                // Currently only maven repositories are taking into account.
                configureArtifactCredentials(project, repo, usernameCredential, passwordCredential)

                /**
                 * Adds the 'subproject' repository to the 'main project' only if does not already exists.
                 */
                addRepoToProject(project, repo)
            }
        }

        project.repositories.configureEach {
            configureArtifactCredentials(project, it, usernameCredential, passwordCredential)
        }

    }

    static void configureArtifactCredentials(Project project, ArtifactRepository artifactRepository, String usernameCredential, String passwordCredential){
        def repoCredentials = artifactRepository["credentials"] as PasswordCredentials
        // Configures only the repositories without credentials.
        if (repoCredentials.getUsername() == null && repoCredentials.getPassword() == null) {
            // Case of nexus repository
            if (FUTIT_REPO_HOST == artifactRepository.getProperties().url.host) {
                repoCredentials.setUsername(project.ext.get("nexusUser"))
                repoCredentials.setPassword(project.ext.get("nexusPassword"))
            } else {
                // Other repos (github, etc...)
                repoCredentials.setUsername(usernameCredential)
                repoCredentials.setPassword(passwordCredential)
            }
        }
    }

}

