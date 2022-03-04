package com.etendoerp.legacy.dependencies.container

import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.FileTree

class EtendoZipModuleArtifact extends ArtifactDependency{

    EtendoZipModuleArtifact(Project project, ResolvedArtifact resolvedArtifact) {
        super(project, resolvedArtifact)
        this.type = DependencyType.ETENDOZIPMODULE
    }

    @Override
    void extract() {
        project.logger.info("")
        project.logger.info("Extracting ZIP module '${this.group}:${this.name}:${this.version}'.")

        // Delete the JAR module if already exists.
        File modulesJarLocation = new File("${project.buildDir.absolutePath}${File.separator}etendo${File.separator}${PublicationUtils.BASE_MODULE_DIR}")
        File jarModule = new File(modulesJarLocation, this.moduleName)

        if (jarModule && jarModule.exists()) {
            project.logger.info("Deleting the JAR module '${jarModule.absolutePath}'")
            jarModule.deleteDir()
        }

        String taskName = "extractZip-${this.moduleName}" + UUID.randomUUID().toString().replace("-","")
        File tempDir = project.tasks.register(taskName).get().temporaryDir

        // Clean tmp dir
        project.delete(tempDir)

        FileTree unzipModule = project.zipTree(this.locationFile)

        // Copy the files to a temporary dir
        project.copy {
            from (unzipModule)
            into (tempDir)
        }

        def sourceModuleLocation = "${project.rootDir.absolutePath}${File.separator}${PublicationUtils.BASE_MODULE_DIR}${File.separator}${moduleName}"

        // Sync the files with the module directory
        project.ant.sync(todir: sourceModuleLocation) {
            ant.fileset(dir: "${tempDir.getAbsolutePath()}${File.separator}${moduleName}")
        }

        // Create metadata file
        EtendoArtifactMetadata artifactMetadata = new EtendoArtifactMetadata(project, DependencyType.ETENDOZIPMODULE, this.group, this.name, this.version)
        artifactMetadata.createMetadataFile(sourceModuleLocation)

        // Clean tmp dir
        project.delete(tempDir)
    }

}
