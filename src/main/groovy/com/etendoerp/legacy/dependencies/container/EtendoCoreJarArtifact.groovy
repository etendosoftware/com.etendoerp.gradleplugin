package com.etendoerp.legacy.dependencies.container

import com.etendoerp.consistency.EtendoArtifactsConsistencyContainer
import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import com.etendoerp.legacy.dependencies.ResolverDependencyLoader
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.FileTree

class EtendoCoreJarArtifact extends ArtifactDependency{

    EtendoCoreJarArtifact(Project project, ResolvedArtifact resolvedArtifact) {
        super(project, resolvedArtifact)
        this.type = DependencyType.ETENDOCOREJAR
    }

    boolean extracted = false

    @Override
    void extract() {
        // Obtain the XML version to perform the version consistency
        File ADModuleFile = getADModuleFile(this.project, this.locationFile)

        if (ADModuleFile) {
            EtendoArtifactMetadata metadata = new EtendoArtifactMetadata(project, this.type)
            if (metadata.loadMetadataFromXML(ADModuleFile.absolutePath)) {
                this.versionParser = metadata.version
            }
        }

        // Run version consistency verification
        // Validate that the module is allowed to be extracted
        EtendoArtifactsConsistencyContainer consistencyContainer = project.ext.get(ResolverDependencyLoader.CONSISTENCY_CONTAINER)
        consistencyContainer.validateArtifact(this)

        // Prevent extracting if the Core JAR already exists and is the same version
        final String coreJarLocation = "${project.buildDir.absolutePath}${File.separator}etendo"

        project.logger.info("Extracting the Etendo core JAR - ${this.group}:${this.name}:${this.version}")

        // TODO: Check if is necessary to preserve 'srcAD' and 'src-gen'.
        FileTree coreFileTree = project.zipTree(this.locationFile)

        def metainfFilter = coreFileTree.matching {
            include "${JAR_ETENDO_LOCATION}"
        }

        /*
            If the system separator is "\" it must be replaced by "/"
            to prevent the java.lang.String.replaceFirst method from
            trying to escape characters in the file path when extracting, causing
            java.util.regex.PatternSyntaxException
        */
        String metaInfPath = "\\" != File.separator ?
                JAR_ETENDO_LOCATION :
                JAR_ETENDO_LOCATION_WOUT_ESCAPED_CHARS

        project.sync {
            from {
                metainfFilter
            }
            into ("${project.buildDir}/etendo")
            eachFile { f ->
                f.path = f.path.replaceFirst(metaInfPath, '')
            }
            includeEmptyDirs = false
        }

        this.extracted = true

        // Create the Artifact metadata file
        EtendoArtifactMetadata metadataToCopy = new EtendoArtifactMetadata(project, DependencyType.ETENDOCOREJAR, this.group, this.name, this.version)
        metadataToCopy.createMetadataFile(coreJarLocation)

        // Sync the config templates
        syncCoreConfig()
    }

    void syncCoreConfig() {
        def etendoConfigLocation = project.file("${project.buildDir}/etendo/config")
        def rootConfigLocation   = project.file("${project.rootDir}/config")
        project.logger.info("Copying 'etendo/config' file to the root project.")

        def configFileTree = project.fileTree(rootConfigLocation).matching {
            include "**/*.template"
        }

        project.logger.info("Deleting template files: ${configFileTree.files}")
        project.delete(configFileTree)

        project.copy {
            from(etendoConfigLocation) {
                include "**/*.template"
            }
            into rootConfigLocation
        }
    }

}
