package com.etendoerp.legacy.utils

import groovy.json.JsonSlurper
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.internal.tasks.userinput.NonInteractiveUserInputHandler
import org.gradle.api.internal.tasks.userinput.UserInputHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Utility class for Nexus operations
 */
class NexusUtils {

    /**
     * Retrieves module information from Nexus API
     * @param user
     * @param password
     * @param group
     * @param name
     * @return List of module versions
     */
    static ArrayList<String> nexusModuleInfo(String user, String password, String group, String name) {
        ArrayList<String> versions = []
        String url = 'https://repo.futit.cloud/service/rest/v1/search?&group=' + group + '&name=' + name
        String userpass = user + ":" + password
        String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()))
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build()
        URI nexus = URI.create(url)
        HttpRequest request = HttpRequest.newBuilder(nexus).GET().header("Authorization", basicAuth).build()
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString())
        def Json = new JsonSlurper().parseText(response.body())
        Json.items.each {
            versions.add(it.version)
        }
        String continuationToken = Json.continuationToken
        while (continuationToken != null) {
            String nextPageUrl = 'https://repo.futit.cloud/service/rest/v1/search?&group=' + group + '&name=' + name + '&continuationToken=' + continuationToken
            URI nextPageUri = URI.create(nextPageUrl)
            HttpRequest req = HttpRequest.newBuilder(nextPageUri).GET().header("Authorization", basicAuth).build()
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString())
            def nextPage = new JsonSlurper().parseText(resp.body())
            nextPage.items.each {
                versions.add(it.version)
            }
            continuationToken = new JsonSlurper().parseText(resp.body()).continuationToken
        }
        return versions
    }

    /**
     * Checks if a module exists in Nexus
     * @param module
     * @return true if module is deployed in Nexus
     */
    static String moduleExistInNexus(String module) {
        def req = new URL('https://api.futit.cloud/migrations/nexus?module_javapackage=' + module).openConnection()
        return req.getResponseCode() == 302
    }

    /**
     * Retrieves Nexus credentials
     * @param project
     * @return List with Nexus user and password
     */
    static def getCredentials(Project project) {
        def nexusUser = ""
        def nexusPassword = ""

        if (project.findProperty("nexusUser") != null && project.findProperty("nexusPassword") != null) {
            nexusUser = project.ext.get("nexusUser")
            nexusPassword = project.ext.get("nexusPassword")
        } else if (System.getProperty("nexusUser") != null && System.getProperty("nexusPassword") != null) {
            nexusUser = System.getProperty("nexusUser")
            nexusPassword = System.getProperty("nexusPassword")
        } else if (project.hasProperty("nexusUser") && project.hasProperty("nexusPassword")) {
            nexusUser = project.property("nexusUser")
            nexusPassword = project.property("nexusPassword")
        }

        return [nexusUser, nexusPassword]
    }

    /**
     * Asks for Nexus credentials if not found
     * @param project
     */
    static void askNexusCredentials(Project project) {
        def (nexusUser, nexusPassword) = getCredentials(project)

        if (!nexusUser || !nexusPassword) {
            def input = project.getServices().get(UserInputHandler.class)
            nexusUser = project.getServices().get(UserInputHandler.class).askQuestion("Nexus user", "")
            nexusPassword = project.getServices().get(UserInputHandler.class).askQuestion("Nexus password", "")
            if (!(input instanceof NonInteractiveUserInputHandler)) {
                input.sendPrompt("\033[F\r" + "Nexus password (default: ): ********* \n")
            }
        }

        if (nexusUser && nexusPassword) {
            project.ext.set("nexusUser", nexusUser)
            project.ext.set("nexusPassword", nexusPassword)
        }

        configureRepositories(project, nexusUser as String, nexusPassword as String)

        project.repositories {
            maven {
                url "https://repo.futit.cloud/repository/maven-releases"
                credentials {
                    username nexusUser
                    password nexusPassword
                }
            }
            maven {
                url "https://repo.futit.cloud/repository/maven-public-releases"
            }
        }
    }

    /**
     * Adds a repository to the project if not already present
     * @param project
     * @param repository
     */
    static void addRepoToProject(Project project, ArtifactRepository repository) {
        String repoUrl = repository.properties.get("url").toString()

        ArtifactRepository projectRepo = project.repositories.find({
            it.properties.get("url")?.toString()?.equalsIgnoreCase(repoUrl)
        })

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
     * Configures all project and subproject repositories with System credentials
     * @param project
     * @param usernameCredential
     * @param passwordCredential
     */
    static void configureRepositories(Project project, String usernameCredential, String passwordCredential) {
        project.logger.info("Starting project repositories configuration.")

        project.subprojects.each {
            it.repositories.each { repo ->
                def repoCredentials = repo["credentials"] as PasswordCredentials

                if (repoCredentials.getUsername() == null && repoCredentials.getPassword() == null && usernameCredential && passwordCredential) {
                    repoCredentials.setUsername(usernameCredential)
                    repoCredentials.setPassword(passwordCredential)
                }

                addRepoToProject(project, repo)
            }
        }

        if (usernameCredential && passwordCredential) {
            project.repositories.configureEach {
                def repoCredentials = it["credentials"] as PasswordCredentials

                if (repoCredentials.getUsername() == null && repoCredentials.getPassword() == null) {
                    repoCredentials.setUsername(usernameCredential)
                    repoCredentials.setPassword(passwordCredential)
                }
            }
        }
    }
}