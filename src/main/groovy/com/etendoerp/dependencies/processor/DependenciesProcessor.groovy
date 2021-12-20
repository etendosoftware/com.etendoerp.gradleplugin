package com.etendoerp.dependencies.processor

import com.etendoerp.dependencies.DependencyArtifact
import org.gradle.api.Project

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
            it.println "                username \"\$mavenUser\""
            it.println "                password \"\$mavenPassword\""
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
