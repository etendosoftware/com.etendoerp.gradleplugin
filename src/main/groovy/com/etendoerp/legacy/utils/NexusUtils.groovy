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

class NexusUtils {

    /** Method to  info about a module in  the Nexus API
     * @return Array<Strings> with module information
     * */
    static ArrayList<String> nexusModuleInfo(String user, String password, String group, String name){
        ArrayList versions = [];
        String url = 'https://repo.futit.cloud/service/rest/v1/search?&group='+group+'&name='+name
        String userpass = user + ":" + password;
        String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()));
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
        URI nexus = URI.create(url);
        HttpRequest request = HttpRequest.newBuilder(nexus).GET().header("Authorization", basicAuth).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        def Json =new JsonSlurper().parseText(response.body())
        Json.items.each{
            versions.add(it.version)
        }
        String continuationToken = Json.continuationToken;
        while (continuationToken != null) {
            String nextPageUrl = 'https://repo.futit.cloud/service/rest/v1/search?&group='+group+'&name='+name+'&continuationToken='+continuationToken;
            URI nextPageUri = URI.create(nextPageUrl)
            HttpRequest req = HttpRequest.newBuilder(nextPageUri).GET().header("Authorization", basicAuth).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            def nextPage =new JsonSlurper().parseText(resp.body())
            nextPage.items.each{
                versions.add(it.version)
            }
            continuationToken = new JsonSlurper().parseText(resp.body()).continuationToken;
        }
        return versions
    }

    /** Method to check in the own Server API if a module is already defined
     * @return true when the module is already deployed in Nexus
     * */
    static String moduleExistInNexus(String module){
        def req = new URL('https://api.futit.cloud/migrations/nexus?module_javapackage='+ module).openConnection()
        return req.getResponseCode()==302
    }

    /**
     * Tries to get user credentials from the project extra properties.
     * If the credentials are not found, searches the properties defined in the gradle.properties file or System properties.
     * A user can set his credentials or System properties in the gradle.properties file adding the lines:
     * nexusUser=nexususer
     * nexusPassword=nexuspassword
     *
     * Or define System properties
     *
     * systemProp.nexusUser=sysUser
     * systemProp.nexusPassword=sysPass
     *
     * Once the credentials are found, it sets its on the Project extra properties
     *
     */
    static askNexusCredentials(Project project) {

        def (nexusUser, nexusPassword) = getCredentials(project)

        if (!nexusUser || ! nexusPassword) {
            def input = project.getServices().get(UserInputHandler.class)
            nexusUser = project.getServices().get(UserInputHandler.class).askQuestion("Nexus user", "")
            nexusPassword = project.getServices().get(UserInputHandler.class).askQuestion("Nexus password", "")
            if (!(input instanceof NonInteractiveUserInputHandler)) {
                // Do not send prompt when using an non interactive console.
                // Avoids a failure when refreshing gradle projects that use this function, in IntelliJ or other IDEs
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

    static def getCredentials(Project project) {
        def nexusUser     = ""
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
     * The credentials could be passed by the command line parameters '-DnexusUser=user -DnexusPassword=password'
     */
    static void configureRepositories(Project project, String usernameCredential, String passwordCredential) {

        project.logger.info("Starting project repositories configuration.")

        /**
         * Gets all the subproject repositories and set its in the root project.
         * This is used to resolved all subproject dependencies.
         */
        project.subprojects.each {
            it.repositories.each {repo ->
                // Currently only maven repositories are taking into account.
                def repoCredentials = repo["credentials"] as PasswordCredentials

                // Configures subproject repositories without credentials.
                if (repoCredentials.getUsername() == null && repoCredentials.getPassword() == null && usernameCredential && passwordCredential) {
                    repoCredentials.setUsername(usernameCredential)
                    repoCredentials.setPassword(passwordCredential)
                }

                /**
                 * Adds the 'subproject' repository to the 'main project' only if does not already exists.
                 */
                addRepoToProject(project, repo)
            }
        }

        if (usernameCredential && passwordCredential) {
            project.repositories.configureEach {
                def repoCredentials = it["credentials"] as PasswordCredentials
                // Configures only the repositories without credentials.
                if (repoCredentials.getUsername() == null && repoCredentials.getPassword() == null) {
                    repoCredentials.setUsername(usernameCredential)
                    repoCredentials.setPassword(passwordCredential)
                }
            }
        }
    }

}
