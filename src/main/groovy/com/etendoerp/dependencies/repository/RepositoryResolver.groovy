package com.etendoerp.dependencies.repository

import com.etendoerp.dependencies.DependenciesLoader
import com.etendoerp.dependencies.DependencyArtifact
import com.etendoerp.legacy.utils.GithubUtils
import groovy.json.JsonSlurper
import org.gradle.api.Project

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Class used to perform a search of a JAR file using sha1 checksums in the Maven and Nexus repositories.
 */
class RepositoryResolver {

    static String MAVEN_REPOSITORY = "https://search.maven.org/"
    static String NEXUS_REPOSITORY = "https://repo.futit.cloud/service/rest/v1/components"

    static Map<String, DependencyArtifact> nexusArtifacts = null

    static void resolveArtifact(Project project, DependencyArtifact artifact) {
        def resolved =  resolveMavenArtifact(artifact)

        if (!resolved) {
            resolveNexusArtifact(project, artifact)
        }

    }

    /**
     * Tries to find a JAR file in the Maven repositories using SHA1 checksums.
     * If found, the 'artifact' is completed with the 'group:name:version'
     * @param artifact
     * @return
     */
    static boolean resolveMavenArtifact(DependencyArtifact artifact) {
        artifact.project.logger.info("Trying to resolve '${artifact.originalName}' using the maven repository ${MAVEN_REPOSITORY}")
        def searchUrl = "${MAVEN_REPOSITORY}solrsearch/select?q=1:${artifact.sha1sum}&rows=20&wt=json"
        // Search for the first result
        def returnpage = new URL(searchUrl).getText()
        def json = new JsonSlurper().parseText(returnpage)
        def jsonResponse = json.response
        def numFound = jsonResponse["numFound"]
        if (numFound == 1) {
            def docs = jsonResponse.docs[0]
            artifact.fullId     = docs["id"]
            artifact.groupId    = docs["g"]
            artifact.artifactId = docs["a"]
            artifact.version    = docs["v"]
            artifact.resolved   = true
            artifact.repositoryLocation = MAVEN_REPOSITORY
            artifact.project.logger.info("Artifact '${artifact.originalName}' has been resolved using the maven repository ${MAVEN_REPOSITORY}")
        } else {
            artifact.resolved = false
            artifact.project.logger.info("Artifact '${artifact.originalName}' could not be resolved using the maven repository ${MAVEN_REPOSITORY}")
        }
        return artifact.resolved
    }

    /**
     * Tries to find a JAR file in the Nexus repositories using SHA1 checksums.
     * If found, the 'artifact' is completed with the 'group:name:version'
     * @param project
     * @param artifact
     * @return
     */
    static boolean resolveNexusArtifact(Project project, DependencyArtifact artifact) {
        artifact.project.logger.info("Trying to resolve '${artifact.originalName}' using the NEXUS repository ${DependenciesLoader.REPOSITORY_TO_PUBLISH}")
        if (!nexusArtifacts) {
            nexusArtifacts = getNexusArtifacts(project, DependenciesLoader.REPOSITORY_TO_PUBLISH)
        }

        def nexusArtifact = nexusArtifacts.get(artifact.sha1sum)

        if (nexusArtifact) {
            project.logger.info("Artifact '${artifact.originalName}' has been resolved using the NEXUS repository ${DependenciesLoader.REPOSITORY_TO_PUBLISH}")
            artifact.groupId    = nexusArtifact.groupId
            artifact.artifactId = nexusArtifact.artifactId
            artifact.version    = nexusArtifact.version
            artifact.fullId     = nexusArtifact.fullId
            artifact.resolved   = true
        } else {
            artifact.resolved = false
            project.logger.info("Artifact '${artifact.originalName}' could not be resolved using the NEXUS repository ${DependenciesLoader.REPOSITORY_TO_PUBLISH}")
        }

        return artifact.resolved
    }

    /**
     * Obtains all the artifacts of a given Nexus repository
     * @param project
     * @param repository
     * @return
     */
    static Map<String, DependencyArtifact> getNexusArtifacts(Project project, String repository) {

        Map<String, DependencyArtifact> nexusArtifacts = new HashMap<>()

        def (nexusUser, nexusPassword) = GithubUtils.getCredentials(project)
        String userpass = nexusUser + ":" + nexusPassword
        String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()))

        final String CONTINUATION_TOKEN_END = "CONTINUATION_TOKEN_END"
        def continuationToken = null

        while (continuationToken != CONTINUATION_TOKEN_END) {
            def tokenParam = continuationToken ? "&continuationToken=${continuationToken}" : ""
            String url = "${NEXUS_REPOSITORY}?repository=${repository}${tokenParam}"

            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build()
            URI nexus = URI.create(url)
            HttpRequest request = HttpRequest.newBuilder(nexus).GET().header("Authorization", basicAuth).build()
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (!response || ! response.body()) {
                project.logger.info("Http response not found for '$repository' repository.")
                break
            }

            def jsonResponse = new JsonSlurper().parseText(response.body())
            continuationToken = jsonResponse.continuationToken

            if (!continuationToken) {
                continuationToken = CONTINUATION_TOKEN_END
            }

            List<Object> items = jsonResponse.items

            if (items) {
                // each item is a dependency
                for (Object item : items) {
                    List<Object> assets = item.assets
                    // each asset is a file from the dependency (.jar, .pom , etc)
                    for (Object asset : assets) {
                        def maven2 = asset.maven2
                        def extension = maven2.extension
                        if (extension == "jar") {
                            // If the asset is the jar file, get the checksum 'sha1'
                            String sha1 = asset.checksum.sha1
                            DependencyArtifact nexusArtifact = new DependencyArtifact()
                            nexusArtifact.sha1sum    = sha1
                            nexusArtifact.groupId    = maven2.groupId
                            nexusArtifact.artifactId = maven2.artifactId
                            nexusArtifact.version    = maven2.version
                            nexusArtifact.fullId     = "${maven2.groupId}:${maven2.artifactId}:${maven2.version}"
                            nexusArtifacts.put(sha1, nexusArtifact)
                            break
                        }
                    }
                }
            }
        }
        return nexusArtifacts
    }

}
