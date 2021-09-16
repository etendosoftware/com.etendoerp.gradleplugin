package com.etendoerp.jars.modules.metadata

import com.etendoerp.legacy.utils.NexusUtils
import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven

/**
 * Class used to contain information about the module being published.
 * Information of this class will be used for maven to generate the corresponding pom.xml
 */
abstract class ModuleMetadata {

    final static String REPOSITORY_ID      = "partner-repo"
    final static String PUBLICATION_DATA   = "deploy.gradle"
    final static String CONFIGURATION_NAME = "moduleDeps"
    final static String ZIP_TYPE           = "zip"

    Project project
    String moduleName
    String group
    String artifactId
    String version
    String repository
    String metadataLocation

    ModuleMetadata(Project project, String moduleName) {
        this.project = project
        this.moduleName = moduleName
        loadMetadata()
    }

    abstract void loadMetadata()
    abstract Node createDependenciesNode()
    abstract String getDependenciesValues()
    abstract void validateDependencies()

    void showModuleMetadata() {
        println("MODULE")
        println("----------")
        println("GROUP: "        + group)
        println("ARTIFACT_ID: "  + artifactId)
        println("VERSION: "      + version)
        println("DEPENDENCIES: " + dependenciesValues)
        println("REPOSITORY: "   + repository)
        println("----------")
    }

    Node createRepositoriesNode() {
        def repositoriesNode = new Node(null, "repositories")
        def repositoryNode = repositoriesNode.appendNode("repository")

        repositoryNode.appendNode("id", REPOSITORY_ID)
        repositoryNode.appendNode("url", this.repository)

        return repositoriesNode
    }

    /**
     * Loads the maven publishing task with all necessary information to create the pom file.
     * @param publishTask The task that will perform the publishing
     * @param artifact The JAR being published.
     */
    void loadMavenTask(AbstractPublishToMaven publishTask, def artifact) {
        publishTask.publication.groupId    = this.group
        publishTask.publication.artifactId = this.artifactId
        publishTask.publication.version    = this.version

        publishTask.publication.artifact(artifact)

        def dependencies = createDependenciesNode()
        def repositories = createRepositoriesNode()
        publishTask.publication.pom.withXml {
            it.asNode().append(dependencies)
            it.asNode().append(repositories)
        }

        if (group != null) {
            project.publishing.repositories.maven.url = repository
            project.publishing.repositories.maven.credentials {
                NexusUtils.askNexusCredentials(project)
                username project.ext.get("nexusUser")
                password project.ext.get("nexusPassword")
            }
            validateDependencies()
        }
    }

}
