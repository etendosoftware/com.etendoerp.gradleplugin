package com.etendoerp.dependencies

import com.etendoerp.dependencies.repository.RepositoryResolver
import com.etendoerp.jars.FileExtensions
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.slf4j.LoggerFactory

class DependencyArtifact {

    final static AntBuilder ant = new AntBuilder()
    String fullId
    String groupId
    String artifactId
    String version
    String url
    String sha1sum
    File fileLocation
    String originalName
    boolean resolved = false
    String repositoryLocation
    String publicationName
    Project project
    Logger log = LoggerFactory.getLogger(this.class.name) as Logger

    DependencyArtifact() {}

    DependencyArtifact(Project project, File artifact) {
        this.project         = project
        this.fileLocation    = artifact
        this.originalName    = artifact.name
        this.publicationName = artifact.name

        ant.checksum(file: artifact.absolutePath, algorithm: "SHA1", property: artifact.name)
        this.sha1sum = ant.project.properties[artifact.name]
        project.logger.info("-")
        project.logger.info("Jar file '${artifact.name}' - SHA1SUM: '${this.sha1sum}'")
        resolveArtifact()
    }

    void resolveArtifact() {
        RepositoryResolver.resolveArtifact(project, this)
    }

    @Override
    String toString() {
        return "Name: $originalName - groupId: $groupId - artifactId: $artifactId - version: $version"
    }

    void generateId() {
        if (this.resolved) {
            throw new IllegalArgumentException("The artifact is already resolved. Should contain the id.")
        }

        if (!originalName) {
            throw new IllegalArgumentException("Artifact original name not found.")
        }

        String artifactName = this.originalName.replace(FileExtensions.JAR, "")
        def splitName = artifactName.split("-")

        String defaultGroup = "com.test"
        String version = "1.0.0"
        int versionIndex

        for (int i = 0; i < splitName.size(); i++) {
            String s = splitName[i]
            if (s?.charAt(0)?.isDigit()) {
                version = s
                versionIndex = i
            }
        }

        // Get the name
        if (versionIndex && versionIndex >= 1) {
            def index = artifactName.lastIndexOf("-${version}")
            if (index >= 1) {
                artifactName = artifactName.substring(0, index)
            }
        }

        this.fullId = "${defaultGroup}:${artifactName}:${version}"
        this.groupId = defaultGroup
        this.artifactId = artifactName
        this.version = version
    }



}
