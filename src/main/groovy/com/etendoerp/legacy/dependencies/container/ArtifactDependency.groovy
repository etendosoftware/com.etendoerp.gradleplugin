package com.etendoerp.legacy.dependencies.container

import com.etendoerp.jars.PathUtils
import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.file.FileTree
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult

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
    
    public static String JAR_ETENDO_MODULE_LOCATION_WOUT_ESCAPED_CHARS = PathUtils.createPathWithCustomSeparator(
            "/",
            PublicationUtils.META_INF,
            PublicationUtils.ETENDO,
            PublicationUtils.BASE_MODULE_DIR
    )

    public static String JAR_ETENDO_LOCATION_WOUT_ESCAPED_CHARS = PathUtils.createPathWithCustomSeparator(
            "/",
            PublicationUtils.META_INF,
            PublicationUtils.ETENDO
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

    // Version used to compare Artifacts
    String versionParser

    // Gradle properties
    ModuleVersionIdentifier moduleVersionIdentifier
    String displayName
    boolean hasConflicts

    // 'jar' or 'zip'
    String extension

    DefaultResolvedDependencyResult dependencyResult
    // Contains the name (group:name)
    String artifactName

    boolean isProjectDependency

    ArtifactDependency(Project project) {
        this.project = project
    }

    ArtifactDependency(Project project, ResolvedArtifact resolvedArtifact) {
        this.project = project
        this.resolvedArtifact = resolvedArtifact
        loadFromArtifact()
    }

    ArtifactDependency(Project project, String moduleName, String version) {
        this.project = project
        this.moduleName = moduleName
        this.version = version
    }

    ArtifactDependency(Project project, String group, String name, String version) {
        this.project = project
        loadModuleVersionIdentifier(group, name, version)
    }

    ArtifactDependency(Project project, String displayName) {
        this.project = project
        this.displayName = replaceSnapshot(displayName)

        def splitName = displayName.split(":")
        if (splitName.size() >= 3) {
            this.group   = splitName[0]
            this.name    = splitName[1]
            this.version = splitName[2]
        }
    }

    ArtifactDependency(Project project, ModuleVersionIdentifier moduleVersionIdentifier, String displayName) {
        this.project = project
        this.moduleVersionIdentifier = moduleVersionIdentifier
        loadFromVersionIdentifier(moduleVersionIdentifier)
        this.displayName = replaceSnapshot(displayName)
    }

    void loadFromVersionIdentifier(ModuleVersionIdentifier moduleVersionIdentifier) {
        this.group = moduleVersionIdentifier.group.toLowerCase()
        this.name = moduleVersionIdentifier.name.toLowerCase()
        this.version = moduleVersionIdentifier.version
        this.moduleName = "${this.group}.${this.name}"
    }

    static String replaceSnapshot(String displayName) {
        if (displayName.contains("SNAPSHOT:")) {
            displayName = displayName.replace("SNAPSHOT:","")
        }
        return displayName
    }

    void loadModuleVersionIdentifier(String group, String name, String version) {
        this.moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId(group, name, version)
        this.group = group
        this.name = name
        this.version = version
        this.moduleName = "${this.group}.${this.name}"
    }

    void loadFromArtifact() {
        this.moduleVersionIdentifier = this.resolvedArtifact.moduleVersion.id
        this.locationFile = this.resolvedArtifact.file
        this.group        = this.resolvedArtifact.moduleVersion.id.group
        this.name         = this.resolvedArtifact.moduleVersion.id.name
        this.version      = this.resolvedArtifact.moduleVersion.id.version
        this.extension    = this.resolvedArtifact.extension
        this.moduleName   = "${this.group}.${this.name}"
        this.displayName  = "${this.group}:${this.name}:${this.version}"
    }

    void extract() {}

    static File getADModuleFile(Project project, File unzipFile) {
        FileTree fileTree = project.zipTree(unzipFile)
        def adModuleLocation = "${JAR_ETENDO_LOCATION}${EtendoArtifactMetadata.AD_MODULE_LOCATION}"

        def adModuleFilter = fileTree.matching {
            include "${adModuleLocation}"
        }

        File adModuleFile = null

        adModuleFilter.each {
            adModuleFile = it
        }

        return adModuleFile
    }

}
