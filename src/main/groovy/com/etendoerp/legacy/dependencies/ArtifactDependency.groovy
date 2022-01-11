package com.etendoerp.legacy.dependencies

import com.etendoerp.jars.JarCoreGenerator
import com.etendoerp.jars.PathUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.FileTree

class ArtifactDependency {

    public static String JAR_ETENDO_LOCATION = PathUtils.createPath(
            PublicationUtils.META_INF,
            PublicationUtils.ETENDO
    )

    public static String JAR_ETENDO_MODULE_LOCATION = PathUtils.createPath(
            PublicationUtils.META_INF,
            PublicationUtils.ETENDO,
            PublicationUtils.BASE_MODULE_DIR
    )

    Project project
    Dependency dependency
    ResolvedArtifact resolvedArtifact
    File locationFile
    DependencyType type
    String group
    String name
    String version
    String moduleName

    // 'jar' or 'zip'
    String extension

    ArtifactDependency(Project project, ResolvedArtifact resolvedArtifact) {
        this.project = project
        this.resolvedArtifact = resolvedArtifact
        loadFromArtifact()
        process()
    }

    void loadFromArtifact() {
        this.locationFile = this.resolvedArtifact.file
        this.group        = this.resolvedArtifact.moduleVersion.id.group
        this.name         = this.resolvedArtifact.moduleVersion.id.name
        this.version      = this.resolvedArtifact.moduleVersion.id.version
        this.extension    = this.resolvedArtifact.extension
        this.moduleName   = "${this.group}.${this.name}"
    }

    void process() {

        // If the extension is 'zip' then could be a Etendo module in zip format
        if (this.extension == "zip") {
            this.type = DependencyType.ETENDOZIPMODULE
            return
        }

        // Default type
        this.type = DependencyType.MAVEN

        // Check if the file is a Etendo module
        FileTree unzipDependency = project.zipTree(this.locationFile)
        def etendoModulesTree = unzipDependency.matching {
            include "${JAR_ETENDO_MODULE_LOCATION}"
        }

        // The dependency file is a Etendo module
        if (etendoModulesTree && etendoModulesTree.size() >= 1) {
            // The jar is the Etendo core
            if (this.locationFile.name.contains(JarCoreGenerator.ETENDO_CORE)) {
                this.type = DependencyType.ETENDOCORE
            } else {
                this.type = DependencyType.ETENDOJARMODULE
            }
        }
    }

    void extract() {
        // extract only jar files
        if (this.extension != "jar") {
            return
        }

        switch (this.type) {
            case DependencyType.ETENDOCORE:
                extractEtendoCore()
                break
            case DependencyType.ETENDOJARMODULE:
                extractEtendoModule()
                break
            default:
                break
        }
    }

    void extractEtendoCore() {
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

    void extractEtendoModule() {
        // Extract only the Etendo jar file if the module is not already in sources - 'modules/' dir
        File modulesLocation = new File("${project.rootDir.absolutePath}${File.separator}${PublicationUtils.BASE_MODULE_DIR}")
        File sourceModule = new File(modulesLocation, this.moduleName)

        if (sourceModule && sourceModule.exists()) {
            project.logger.error("The JAR module '${moduleName}' already exists in the 'modules/' directory. Skipping extraction.")
            return
        }

        project.logger.info("Extracting the Etendo module JAR '${this.moduleName}'")

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

    }

}
