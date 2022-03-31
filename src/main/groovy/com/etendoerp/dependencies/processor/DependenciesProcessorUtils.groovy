package com.etendoerp.dependencies.processor

import com.etendoerp.dependencies.DependencyArtifact
import org.gradle.api.Project

class DependenciesProcessorUtils {

    public static String PUBLISH_LIST_NAME     = "publishList"
    public static String PUBLISH_DESTINE       = "ToMavenRepository"

    static String generateRepositories(List<String> repos) {
        String repositories = ""
        repositories += "repositories {                         \n"
        repositories += "   mavenCentral()                      \n"
        repositories += generateMavenRepositoriesList(repos)
        repositories += "}                                      \n"
        return repositories
    }

    static String generateMavenRepositoriesList(List<String> repositories) {
        String repoList = ""
        for (String repository : repositories) {
            repoList += "   maven {                              \n"
            repoList += "       url '${repository}'              \n"
            repoList += "       credentials {                    \n"
            repoList += "           username \"\$mavenUser\"     \n"
            repoList += "           password \"\$mavenPassword\" \n"
            repoList += "       }                                \n"
            repoList += "   }                                    \n"
        }
        return repoList
    }

    static String generateArtifactsInformation(Map artifactsData) {
        String info = ""
        info += "// Listing jar files - scope: ${artifactsData.scope.toString()} \n"
        info += "// Total artifacts      = ${artifactsData.total.size()}         \n"
        info += "// Resolved artifacts   = ${artifactsData.resolved.size()}      \n"
        info += "// Unresolved artifacts = ${artifactsData.unresolved.size()}    \n"
        return  info
    }

    static String generateDependenciesList(Project project, List<DependencyArtifact>  artifacts, JarScope scope) {

        def dependenciesList = ""
        artifacts.each {
            dependenciesList += "   ${scope.getConfiguration()}('${it.fullId}') { transitive = false }\n"
        }

        if (dependenciesList.size() > 0) {
            dependenciesList = dependenciesList.substring(0, dependenciesList.size() - 1)
        }

        return dependenciesList
    }

    static String generatePublicationList(Project project, List<DependencyArtifact> artifacts) {
        String publicationList = ""
        artifacts.each {
            it.generateId()
            publicationList += "        \"${it.publicationName}\"(MavenPublication) {                  \n"
            publicationList += "            groupId    '${it.groupId}'                                 \n"
            publicationList += "            artifactId '${it.artifactId}'                              \n"
            publicationList += "            version    '${it.version}'                                 \n"
            publicationList += "            artifact(new File('${it.fileLocation.getAbsolutePath()}')) \n"
            publicationList += "        }                                                              \n"
        }
        return publicationList
    }

    static String generateTasksToPublishList(Project project, List<DependencyArtifact> artifacts, JarScope scope) {
        String auxList = ""
        artifacts.each {
            def publishName = it.publicationName.capitalize()
            auxList += "    'publish${publishName}Publication${PUBLISH_DESTINE}',\n"
        }

        if (auxList.size() >= 2) {
            auxList = auxList.substring(0, auxList.size() - 2)
        }

        String publishList = ""
        publishList += "def ${PUBLISH_LIST_NAME}${scope.toString()} = [ \n"
        publishList += "${auxList}                   \n"
        publishList += "]                            \n"

        return publishList
    }

    static String generateTasksToPublish(Project project, List<DependencyArtifact> artifacts, JarScope scope) {
        String publicationList = generateTasksToPublishList(project, artifacts, scope)
        publicationList += "                                                              \n"
        publicationList += "project.tasks.register(\"publishUnresolved${scope.toString().toLowerCase().capitalize()}Jars\") {        \n"
        publicationList += "    dependsOn(${PUBLISH_LIST_NAME}${scope.toString()})        \n"
        publicationList += "    doLast {                                                  \n"
        publicationList += "        project.logger.info('Unresolved jars published.')     \n"
        publicationList += "    }                                                         \n"
        publicationList += "}                                                             \n"

        return publicationList
    }

    static String generateRepositoryToPublish(Project project, String repository) {
        String repo = ""
        repo += "project.publishing.repositories.maven.url = '${repository}'\n"
        repo += "project.publishing.repositories.maven.credentials {\n"
        repo += "   username \"\$mavenUser\"                        \n"
        repo += "   password \"\$mavenPassword\"                    \n"
        repo += "}\n"
        return repo
    }
}
