package com.etendoerp.jars.modules

import com.etendoerp.jars.PathUtils
import org.gradle.api.Project

/**
 * Class used to contain information about the module being published.
 * Information of this class will be used for maven to generate the corresponding pom.xml
 */
class ModuleMetadata {

    final static String REPOSITORY_ID = "partner-repo"

    Project project
    String moduleName
    String group
    String artifactId
    String version
    String repository
    String metadataLocation
    List<String> dependencies = new ArrayList<>()

    ModuleMetadata(Project project, String moduleName) {
        this.project = project
        this.moduleName = moduleName
        loadMetadataLocation()
        loadMetadata()
    }

    void loadMetadataLocation() {
        metadataLocation = PathUtils.createPath(
                project.rootDir.absolutePath,
                ModuleJarGenerator.BASE_MODULE_DIR,
                moduleName
        ).concat(ModuleJarPublication.PUBLICATION_DATA)

        if (!project.file(metadataLocation).exists()) {
            throw new IllegalArgumentException("The file '$metadataLocation' does not exists.")
        }
    }

    void loadMetadata() {
        BufferedReader br_build = new BufferedReader(new FileReader(metadataLocation))
        String line
        while ((line = br_build.readLine()) != null) {
            if (line.startsWith("//")){
                continue
            }
            if (line.contains("group") && line.contains("=")){
                group = line.split("=")[1].trim().replace("'", "")
            }
            if (line.contains("version") && line.contains("=")){
                version = line.split("=")[1].trim().replace("'", "")
            }
            if (line.contains("compile")&& line.contains(" ")){
                dependencies.add(line.trim().split(" ")[1].replace("'", ""))
            }
            if (line.contains("url")){
                repository = line.trim().split(" ")[1].replace("\"", "")
            }
        }
        br_build.close()
        artifactId = moduleName.toString().replace(group + ".", "")
    }

    void showModuleMetadata() {
        println("MODULE")
        println("----------")
        println("GROUP: "        + group)
        println("ARTIFACT_ID: "  + artifactId)
        println("VERSION: "      + version)
        println("DEPENDENCIES: " + dependencies)
        println("REPOSITORY: "   + repository)
        println("----------")
    }

    Node createDependenciesNode() {
        def dependenciesNode = new Node(null, "dependencies")

        this.dependencies.each {
            def dependencyNode = dependenciesNode.appendNode("dependency")
            def elements = it.toString().split(":")
            def versionElements = elements[2].split("@")

            dependencyNode.appendNode("groupId", elements[0])
            dependencyNode.appendNode("artifactId", elements[1])
            dependencyNode.appendNode("version", versionElements[0])

            // Check if the version contains the zip type.
            if (versionElements.size() >= 2) {
                dependencyNode.appendNode("type", versionElements[1])
            }

        }
        return dependenciesNode
    }

    Node createRepositoriesNode() {
        def repositoriesNode = new Node(null, "repositories")
        def repositoryNode = repositoriesNode.appendNode("repository")

        repositoryNode.appendNode("id", REPOSITORY_ID)
        repositoryNode.appendNode("url", this.repository)

        return repositoriesNode
    }

}
