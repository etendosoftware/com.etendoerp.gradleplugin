package com.etendoerp.legacy.dependencies.container

import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.FileTree

class EtendoCoreArtifact extends ArtifactDependency{

    EtendoCoreArtifact(Project project, ResolvedArtifact resolvedArtifact) {
        super(project, resolvedArtifact)
        this.type = DependencyType.ETENDOCORE
    }

    @Override
    void extract() {
        // Prevent extracting if the Core JAR already exists and is the same version
        EtendoArtifactMetadata coreMetadata = new EtendoArtifactMetadata(project, DependencyType.ETENDOCORE)
        final String coreJarLocation = "${project.buildDir.absolutePath}${File.separator}etendo"

        if (coreMetadata.loadMetadataFile(coreJarLocation)) {
            def currentCoreJarVersion = coreMetadata.version

            if (currentCoreJarVersion && currentCoreJarVersion == this.version) {
                project.logger.info("Etendo core Jar version '${this.version}' already extracted.")
                return
            }
        }

        project.logger.info("Extracting the Etendo core JAR - ${this.group}:${this.name}:${this.version}")

        // TODO: Check if is necessary to preserve 'srcAD' and 'src-gen'.

        FileTree coreFileTree = project.zipTree(this.locationFile)

        def metainfFilter = coreFileTree.matching {
            include "${JAR_ETENDO_LOCATION}"
        }

        project.sync {
            from {
                metainfFilter
            }
            into ("${project.buildDir}/etendo")
            eachFile { f ->
                f.path = f.path.replaceFirst("${JAR_ETENDO_LOCATION}", '')
            }
            includeEmptyDirs = false
        }

        // Create the Artifact metadata file
        EtendoArtifactMetadata metadataToCopy = new EtendoArtifactMetadata(project, DependencyType.ETENDOCORE)
        metadataToCopy.group = this.group
        metadataToCopy.name = this.name
        metadataToCopy.version = this.version
        metadataToCopy.createMetadataFile(coreJarLocation)

        // Sync the config templates
        syncCoreConfig()
    }

    void syncCoreConfig() {
        def etendoConfigLocation = project.file("${project.buildDir}/etendo/config")
        def rootConfigLocation   = project.file("${project.rootDir}/config")
        project.logger.info("Copying 'etendo/config' file to the root project.")

        project.delete(rootConfigLocation) {
            include("**/*.template")
        }

        project.copy {
            from(etendoConfigLocation) {
                include("**/*.template")
            }
            into rootConfigLocation
        }
    }

}
