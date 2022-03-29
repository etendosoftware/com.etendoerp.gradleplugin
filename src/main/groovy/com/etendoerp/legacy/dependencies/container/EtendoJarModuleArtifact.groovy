package com.etendoerp.legacy.dependencies.container

import com.etendoerp.consistency.EtendoArtifactsConsistencyContainer
import com.etendoerp.jars.PathUtils
import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import com.etendoerp.legacy.dependencies.ResolverDependencyLoader
import com.etendoerp.legacy.utils.ModulesUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.FileTree
import org.gradle.internal.impldep.org.junit.platform.commons.util.ModuleUtils

class EtendoJarModuleArtifact extends ArtifactDependency{

    EtendoJarModuleArtifact(Project project, ResolvedArtifact resolvedArtifact) {
        super(project, resolvedArtifact)
        this.type = DependencyType.ETENDOJARMODULE
    }

    boolean extracted = false

    @Override
    void extract() {
        // TODO: Improvement - Use the result of the 'resolutionConflicts' to verify if the module contains conflicts.

        // Extract only the Etendo jar file if the module is not already in sources - 'modules/' dir
        File modulesLocation = new File("${project.rootDir.absolutePath}${File.separator}${PublicationUtils.BASE_MODULE_DIR}")
        File sourceModule = ModulesUtils.searchFileInDirIgnoreCase(project, modulesLocation.absolutePath, this.moduleName)

        if (sourceModule && sourceModule.exists()) {
            this.extracted = false
            project.logger.info("The JAR module '${moduleName}' already exists in the 'modules/' directory. Skipping extraction. Artifact '${this.group}:${this.name}:${this.version}" )
            return
        }

        // Validate that the module is allowed to be extracted
        this.versionParser = this.version
        EtendoArtifactsConsistencyContainer consistencyContainer = project.ext.get(ResolverDependencyLoader.CONSISTENCY_CONTAINER)
        consistencyContainer.validateArtifact(this)

        project.logger.info("")
        project.logger.info("Extracting the Etendo module JAR '${this.group}:${this.name}:${this.version}'")

        final String etendoModulesLocation = PathUtils.createPath(
                project.buildDir.absolutePath,
                PublicationUtils.ETENDO,
                PublicationUtils.BASE_MODULE_DIR
        )

        FileTree moduleFileTree = project.zipTree(this.locationFile)

        def metainfFilter = moduleFileTree.matching {
            include "${JAR_ETENDO_LOCATION}"
        }

        def srcFilter = moduleFileTree.matching {
            include '**/*'
            exclude 'META-INF/**'
            exclude '**/*.class'
        }

        project.sync {
            from {
                metainfFilter
            }
            into "${etendoModulesLocation}${this.moduleName}"
            eachFile { f ->
                f.path = f.path.replaceFirst("${JAR_ETENDO_MODULE_LOCATION}${this.moduleName}", '')
            }
            includeEmptyDirs = false
        }

        project.sync {
            from {
                srcFilter
            }
            into "${etendoModulesLocation}${this.moduleName}/src"
            includeEmptyDirs = false
        }

        this.extracted = true

        // Create the Artifact metadata file
        EtendoArtifactMetadata metadataToCopy = new EtendoArtifactMetadata(project, DependencyType.ETENDOJARMODULE, this.group, this.name, this.version)
        metadataToCopy.createMetadataFile("${etendoModulesLocation}${this.moduleName}")
    }

}
