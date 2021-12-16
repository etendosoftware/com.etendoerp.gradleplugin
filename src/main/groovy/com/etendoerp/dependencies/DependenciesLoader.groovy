package com.etendoerp.dependencies

import org.gradle.api.Project

class DependenciesLoader {
    public static String REPOSITORY_TO_PUBLISH = "etendo-internal-dependencies"
    public static String URL_TO_PUBLISH        = "https://repo.futit.cloud/repository/"
    public static String PUBLISH_DESTINE       = "ToMavenLocal"
    public static String PUBLISH_LIST_NAME     = "publishList"

    public static String RESOLVED_ARTIFACTS_FILE_NAME   = "build.resolved.artifacts.gradle"
    public static String UNRESOLVED_ARTIFACTS_FILE_NAME = "build.unresolved.artifacts.gradle"

    public static List<String> EXCLUDED_JARS = [
            "**/openbravo-core.jar",
            "**/dbsourcemanager.jar",
            "**/dbmanager.jar",
            "**/openbravo-wad.jar",
            "**/openbravo-trl.jar"
    ]

    static void load(Project project) {
        project.tasks.register("searchJarDependency") {
            doLast {

                // Default location to search jars files
                def locationToSearch = project.rootDir

                // Get user repository
                String repoParameter = project.findProperty("repo")
                if (repoParameter) {
                    REPOSITORY_TO_PUBLISH = repoParameter
                }
                URL_TO_PUBLISH = URL_TO_PUBLISH + REPOSITORY_TO_PUBLISH

                // Get location to search
                String locationParameter = project.findProperty("location")
                if (locationParameter) {
                    locationToSearch = project.file(locationParameter)
                }

                List<DependencyArtifact> dependencyArtifactList = []

                def jarFiles = project.fileTree(locationToSearch).matching {
//                    include ("**/ant-1.9.2.jar")
                    include ("lib/build/*.jar")
//                    include("**/itext-pdfa-5.5.0.jar")
//                    include("**/itextpdf-5.5.0.jar")
                    exclude (EXCLUDED_JARS)
                    exclude ("**/gradle/**")
                    exclude ("**/WebContent/WEB-INF/**")
                    exclude ("**/WEB-INF/lib/**")
                    exclude ("${project.buildDir}/**")
                    exclude ("buildSrc/**")
                }

                jarFiles.each {
                    def artifact = new DependencyArtifact(project, it)
                    dependencyArtifactList.add(artifact)
                }

                processArtifacts(project, dependencyArtifactList)
            }
        }

    }

    static void processArtifacts(Project project, List<DependencyArtifact> artifacts) {
        project.logger.info("Processing artifacts.")
        def artifactsSize = artifacts.size()
        project.logger.info("Total artifacts size: $artifactsSize")

        List<DependencyArtifact> resolvedArtifacts   = []
        List<DependencyArtifact> unresolvedArtifacts = []

        Map artifactsData = [:]
        artifactsData["total"]      = artifacts
        artifactsData["resolved"]   = resolvedArtifacts
        artifactsData["unresolved"] = unresolvedArtifacts

        artifacts.each {
            if (it.resolved) {
                resolvedArtifacts.add(it)
            } else {
                unresolvedArtifacts.add(it)
            }
        }

        project.logger.info("Total artifacts resolved: ${resolvedArtifacts.size()}")
        project.logger.info("Total artifacts unresolved: ${unresolvedArtifacts.size()}")
        processResolvedArtifacts(project, artifactsData)
        processUnresolvedArtifacts(project, artifactsData)
    }

    static void processResolvedArtifacts(Project project, Map artifactsData) {
        def destinationFile = new File(RESOLVED_ARTIFACTS_FILE_NAME)
        List<DependencyArtifact> resolvedArtifacts = artifactsData["resolved"] as List<DependencyArtifact>
        destinationFile.text = ""
        destinationFile.withWriter {
            it.println "${artifactsInformation(artifactsData)}"
            it.println "${generateRepositories()}"
            it.println "// Listing only resolved artifacts."
            it.println "dependencies {"
            it.println "${generateDependenciesList(project, resolvedArtifacts)}"
            it.println "}"
        }
    }

    static String generateRepositories() {
        String repositories = ""
        repositories += "repositories {                        \n"
        repositories += "   mavenCentral()                     \n"
        repositories += "   maven {                            \n"
        repositories += "       url '${URL_TO_PUBLISH}'        \n"
        repositories += "       credentials {                  \n"
        repositories += "           username '\$mavenUser'     \n"
        repositories += "           password '\$mavenPassword' \n"
        repositories += "       }                              \n"
        repositories += "   }                                  \n"
        repositories += "}                                     \n"
        return repositories
    }

    static String artifactsInformation(Map artifactsData) {
        String info = ""
        info += "// Total artifacts      = ${artifactsData.total.size()} \n"
        info += "// Resolved artifacts   = ${artifactsData.resolved.size()} \n"
        info += "// Unresolved artifacts = ${artifactsData.unresolved.size()} \n"
        return  info
    }

    static String generateDependenciesList(Project project, List<DependencyArtifact>  artifacts) {
        def dependenciesList = ""
        artifacts.each {
            dependenciesList += "   implementation '${it.fullId}' \n"
        }

        if (dependenciesList.size() > 0) {
            dependenciesList = dependenciesList.substring(0, dependenciesList.size() - 1)
        }

        return dependenciesList
    }

    static void processUnresolvedArtifacts(Project project, Map artifactsData) {
        def destinationFile = new File(UNRESOLVED_ARTIFACTS_FILE_NAME)
        List<DependencyArtifact> unresolvedArtifacts= artifactsData["unresolved"] as List<DependencyArtifact>
        destinationFile.text = ""
        destinationFile.withWriter {
            it.println "${artifactsInformation(artifactsData)}"
            it.println "// Listing only unresolved artifacts."
            it.println "publishing {"
            it.println "    publications {"
            it.println "${generatePublicationList(project, unresolvedArtifacts)}"
            it.println "    }"
            it.println "    repositories {"
            it.println "        maven { "
            it.println "            credentials {"
            it.println "                username '\$mavenUser'"
            it.println "                password '\$mavenPassword'"
            it.println "            }"
            it.println "            url '${URL_TO_PUBLISH}'"
            it.println "        }"
            it.println "    }"
            it.println "}"
            it.println ""
            it.println generateTasksToPublishList(project, unresolvedArtifacts)
            it.println ""
            it.println "project.tasks.register(\"publishUnresolvedModules\") {"
            it.println "    dependsOn(${PUBLISH_LIST_NAME})"
            it.println "    doLast {"
            it.println "        project.logger.info('Unresolved modules publicated.')"
            it.println "    }"
            it.println "}"
        }

    }

    static String generatePublicationList(Project project, List<DependencyArtifact> artifacts) {
        String publicationList = ""
        artifacts.each {
            it.generateId()
            publicationList += "        \"${it.publicationName}\"(MavenPublication) {\n"
            publicationList += "            groupId    '${it.groupId}' \n"
            publicationList += "            artifactId '${it.artifactId}' \n"
            publicationList += "            version    '${it.version}' \n"
            publicationList += "            artifact(new File('${it.fileLocation.getAbsolutePath()}'))\n"
            publicationList += "        }\n"
        }
        return publicationList
    }

    static String generateTasksToPublishList(Project project, List<DependencyArtifact> artifacts) {
        String auxList = ""
        artifacts.each {
            def publishName = it.publicationName.capitalize()
            auxList += "    'publish${publishName}Publication${PUBLISH_DESTINE}',\n"
        }

        if (auxList.size() >= 2) {
            auxList = auxList.substring(0, auxList.size() - 2)
        }

        String publishList = ""
        publishList += "def ${PUBLISH_LIST_NAME} = [\n"
        publishList += "${auxList} \n"
        publishList += "]"

        return publishList
    }

}
