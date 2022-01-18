package com.etendoerp.legacy.dependencies.container

import com.etendoerp.jars.PathUtils
import com.etendoerp.legacy.dependencies.container.DependencyType
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier

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

    // Gradle properties
    ModuleVersionIdentifier moduleVersionIdentifier
    String displayName
    boolean hasConflicts

    // 'jar' or 'zip'
    String extension

    ArtifactDependency(Project project, ResolvedArtifact resolvedArtifact) {
        this.project = project
        this.resolvedArtifact = resolvedArtifact
        loadFromArtifact()
    }

    ArtifactDependency(Project project, String group, String name, String version) {
        this.project = project
        loadModuleVersionIdentifier(group, name, version)
    }

    ArtifactDependency(Project project, ModuleVersionIdentifier moduleVersionIdentifier, String displayName) {
        this.project = project
        this.moduleVersionIdentifier = moduleVersionIdentifier

        if (displayName.contains("SNAPSHOT:")) {
            displayName = displayName.replace("SNAPSHOT:","")
        }

        this.displayName = displayName
    }

    void loadModuleVersionIdentifier(String group, String name, String version) {
        this.moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId(group, name, version)
        this.group = group
        this.name = name
        this.version = version
    }

    void loadFromArtifact() {
        this.moduleVersionIdentifier = this.resolvedArtifact.moduleVersion.id
        this.locationFile = this.resolvedArtifact.file
        this.group        = this.resolvedArtifact.moduleVersion.id.group
        this.name         = this.resolvedArtifact.moduleVersion.id.name
        this.version      = this.resolvedArtifact.moduleVersion.id.version
        this.extension    = this.resolvedArtifact.extension
        this.moduleName   = "${this.group}.${this.name}"
        this.displayName = "${this.group}:${this.name}:${this.version}"
    }

    void extract() {}

}
