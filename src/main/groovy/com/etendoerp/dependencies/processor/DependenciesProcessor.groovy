package com.etendoerp.dependencies.processor

import com.etendoerp.dependencies.DependencyArtifact
import org.gradle.api.Project

/**
 * Class used to filter in a 'location' JARs files.
 *
 * For each JAR file found, a verification is performed to see if is already in a Maven repository or needs to be uploaded to Nexus.
 *
 * Two files are generated depending of the status of the JAR.
 *  Resolved: Found in a Maven repository
 *  Unresolved: Needs to be uploaded to Nexus
 *
 * The resolved file contains the dependency of the JAR, using the 'implementation' configuration.
 *
 * The unresolved file contains the necessary tasks to publish the JARs files to a Nexus repository.
 *
 * Two scopes are supported, COMPILATION and TEST
 *  COMPILATION: Contains the JARs files found in the 'locationToSearch' which NOT are under a 'test/' directory
 *
 *  TEST: Contains the JARs files found in the 'locationToSearch' which are under a 'test/' directory.
 *
 *  EX: /location
 *       |---- lib
 *              |---- runtime
 *                        |---- catalina-ant.jar
 *              |----- test
 *                        |---- junit-4.12.jar
 *
 *  The COMPILATION filter will contain the 'catalina-ant.jar'
 *  The TEST filter will contain the 'junit-4.12.jar'
 *
 */
class DependenciesProcessor {

    Project project
    File locationToSearch
    File destination
    String repository
    String nexusRepository

    public static String RESOLVED_ARTIFACTS_PREFIX   = "resolved"
    public static String UNRESOLVED_ARTIFACTS_PREFIX = "unresolved"

    public static String RESOLVED_ARTIFACTS_SUFFIX   = "artifacts.gradle"
    public static String UNRESOLVED_ARTIFACTS_SUFFIX = "artifacts.gradle"

    public static List<String> EXCLUDED_JARS = [
            "**/openbravo-core.jar",
            "**/dbsourcemanager.jar",
            "**/dbmanager.jar",
            "**/openbravo-wad.jar",
            "**/openbravo-trl.jar",
            "**/ob-rhino-1.6R7.jar"
    ]

    public static List<String> EXCLUDED_LOCATIONS = [
            "**/gradle/**",
            "**/WebContent/WEB-INF/**",
            "**/WEB-INF/lib/**",
            "buildSrc/**"
    ]

    DependenciesProcessor(Project project, File locationToSearch, File destination, String repository, String nexusRepository) {
        this.project          = project
        this.locationToSearch = locationToSearch
        this.destination      = destination
        this.repository       = repository
        this.nexusRepository  = nexusRepository
    }

    void process() {
        if (!locationToSearch || !locationToSearch.exists()) {
            throw new IllegalArgumentException("The location to search '${locationToSearch}' does not exists.")
        }

        if (!destination || !destination.exists()) {
            throw new IllegalArgumentException("The destination '${destination}' does not exists.")
        }

        project.logger.info("Starting dependencies process.")
        project.logger.info("Location to search: ${locationToSearch}")
        project.logger.info("Destination: ${destination}")
        project.logger.info("Nexus repository: ${nexusRepository}")

        processJarFiles(JarScope.COMPILATION)
        processJarFiles(JarScope.TEST)
    }

    void processJarFiles(JarScope scope) {

        project.logger.info("Processing '${scope.toString()}' jar files.")

        List<DependencyArtifact> dependencyArtifactList = []
        List<String> includedPaths = []
        List<String> excludedPaths = []

        if (scope == JarScope.COMPILATION) {
            includedPaths.add("**/*.jar")
            excludedPaths.add("**/test/**")
        } else if (scope == JarScope.TEST) {
            includedPaths.add("**/test/**/*.jar")
        }

        def jarFiles = project.fileTree(locationToSearch).matching {
            include (includedPaths)
            exclude (excludedPaths)
            exclude (EXCLUDED_JARS)
            exclude (EXCLUDED_LOCATIONS)
            exclude ("${project.buildDir}/**")
        }

        jarFiles.each {
            def artifact = new DependencyArtifact(project, it)
            dependencyArtifactList.add(artifact)
        }
        processArtifacts(project, dependencyArtifactList, scope)
    }

    void processArtifacts(Project project, List<DependencyArtifact> artifacts, JarScope scope) {
        project.logger.info("Starting proccess to create the build files - Scope: ${scope.toString()}.")
        def artifactsSize = artifacts.size()
        project.logger.info("Total artifacts size: $artifactsSize")

        List<DependencyArtifact> resolvedArtifacts   = []
        List<DependencyArtifact> unresolvedArtifacts = []

        Map artifactsData = [:]
        artifactsData["scope"]      = scope
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
        processResolvedArtifacts(project, artifactsData, scope)
        processUnresolvedArtifacts(project, artifactsData, scope)
    }

    void processResolvedArtifacts(Project project, Map artifactsData, JarScope scope) {
        String resolvedArtifactsFileName = "${RESOLVED_ARTIFACTS_PREFIX}.${scope.toString()}.${RESOLVED_ARTIFACTS_SUFFIX}"
        def destinationFile = new File(destination, resolvedArtifactsFileName)
        List<DependencyArtifact> resolvedArtifacts = artifactsData["resolved"] as List<DependencyArtifact>

        //

        def generateList = project.findProperty("list")
        if (generateList) {
            generateDependenciesFileList(resolvedArtifacts, scope)
        }

        destinationFile.text = ""
        destinationFile.withWriter {
            it.println "${DependenciesProcessorUtils.generateArtifactsInformation(artifactsData)}"
            it.println "${DependenciesProcessorUtils.generateRepositories([nexusRepository])}"
            it.println "// Listing only resolved artifacts."
            it.println "dependencies {"
            it.println "${DependenciesProcessorUtils.generateDependenciesList(project, resolvedArtifacts, scope)}"
            it.println "}"
        }
    }

    void generateDependenciesFileList(List<DependencyArtifact> artifacts, JarScope scope) {
        String resolvedArtifactsFileName = "artifacts.list.${scope.toString()}.gradle"
        def destinationFile = new File(destination, resolvedArtifactsFileName)
        destinationFile.text = ""
        String dependenciesBlock = "[ \n"

        for (DependencyArtifact artifact : artifacts) {
            String id = "${artifact.groupId}:${artifact.artifactId}:${artifact.version}"
            dependenciesBlock += "  '${id}', \n"
        }

        dependenciesBlock += "]"

        final String variableListName = "dependenciesList${scope.toString()}"

        String dependencies = "List<String> _${variableListName} = ${dependenciesBlock} \n"
        destinationFile.text = "${dependencies} \n"

        destinationFile.text += "ext { \n"
        destinationFile.text += "   ${variableListName} = _${variableListName} \n"
        destinationFile.text += "}\n"
    }

    void processUnresolvedArtifacts(Project project, Map artifactsData, JarScope scope) {
        String unresolvedArtifactsFileName = "${UNRESOLVED_ARTIFACTS_PREFIX}.${scope.toString()}.${UNRESOLVED_ARTIFACTS_SUFFIX}"
        def destinationFile = new File(destination, unresolvedArtifactsFileName)
        List<DependencyArtifact> unresolvedArtifacts= artifactsData["unresolved"] as List<DependencyArtifact>
        destinationFile.text = ""
        destinationFile.withWriter {
            it.println "${DependenciesProcessorUtils.generateArtifactsInformation(artifactsData)}"
            it.println "${DependenciesProcessorUtils.generateRepositoryToPublish(project, nexusRepository)}"
            it.println "// Listing only unresolved artifacts."
            it.println "publishing {"
            it.println "    publications {"
            it.println "${DependenciesProcessorUtils.generatePublicationList(project, unresolvedArtifacts)}"
            it.println "    }"
            it.println "    repositories {"
            it.println "        maven { "
            it.println "            credentials {"
            it.println "                username \"\$nexusUser\""
            it.println "                password \"\$nexusPassword\""
            it.println "            }"
            it.println "            url '${nexusRepository}'"
            it.println "        }"
            it.println "    }"
            it.println "}"
            it.println ""
            it.println DependenciesProcessorUtils.generateTasksToPublish(project, unresolvedArtifacts, scope)
        }
    }

}
