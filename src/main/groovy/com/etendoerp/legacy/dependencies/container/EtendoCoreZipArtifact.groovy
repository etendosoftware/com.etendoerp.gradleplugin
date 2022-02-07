package com.etendoerp.legacy.dependencies.container

import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.FileTree

class EtendoCoreZipArtifact extends ArtifactDependency {

    File tempDir

    EtendoCoreZipArtifact(Project project, ResolvedArtifact resolvedArtifact) {
        super(project, resolvedArtifact)
        this.type = DependencyType.ETENDOCOREZIP
    }

    @Override
    void extract() {
        // Check tmp dir
        if (!this.tempDir) {
            throw new IllegalArgumentException("The temporal dir to extract the Core in SOURCES is not defined.")
        }

        // TODO: Check core version with the current installed one
        // If the version is minor prevent extracting (Allow user to add Force flag)

        FileTree unzipCore = project.zipTree(this.locationFile)

        // Copy the files to a temporary dir
        project.copy {
            from (unzipCore)
            into (this.tempDir)
        }

        // Create the Artifact metadata file
        EtendoArtifactMetadata metadataToCopy = new EtendoArtifactMetadata(project, this.type, this.group, this.name, this.version)
        metadataToCopy.createMetadataFile(this.tempDir.absolutePath)
    }

}
