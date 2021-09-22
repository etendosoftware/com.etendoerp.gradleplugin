package com.etendoerp.jars.modules.metadata

import com.etendoerp.jars.PathUtils
import com.etendoerp.jars.modules.ModuleJarGenerator
import com.etendoerp.jars.modules.ModuleJarPublication
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project

/**
 * This class is used to obtain all the necessary information to publish.
 * The information will be took from a 'deploy.gradle' file.
 */
class ModuleDeployMetadata extends ModuleMetadata{

    // Stores dependencies with format "group:artifact:version@type"
    List<String> dependencies

    ModuleDeployMetadata(Project project, String moduleName) {
        super(project, moduleName)
    }

    void loadMetadataLocation() {
        metadataLocation = PathUtils.createPath(
                project.rootDir.absolutePath,
                PublicationUtils.BASE_MODULE_DIR,
                moduleName
        ).concat(ModuleJarPublication.PUBLICATION_DATA)

        if (!project.file(metadataLocation).exists()) {
            throw new IllegalArgumentException("The file '$metadataLocation' does not exists.")
        }
    }

    @Override
    void loadMetadata() {
        this.dependencies = new ArrayList<>()
        loadMetadataLocation()
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
            if (line.contains("compile") && line.contains(" ")){
                dependencies.add(line.trim().split(" ")[1].replace("'", ""))
            }
            if (line.contains("url")){
                repository = line.trim().split(" ")[1].replace("\"", "")
            }
        }
        br_build.close()
        artifactId = moduleName.toString().replace(group + ".", "")
    }

    @Override
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

    @Override
    String getDependenciesValues() {
        return this.dependencies.toString()
    }

    @Override
    void validateDependencies() {

    }
}
