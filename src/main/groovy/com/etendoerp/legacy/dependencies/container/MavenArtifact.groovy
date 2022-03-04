package com.etendoerp.legacy.dependencies.container

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact

class MavenArtifact extends ArtifactDependency{

    MavenArtifact(Project project, ResolvedArtifact resolvedArtifact) {
        super(project, resolvedArtifact)
        this.type = DependencyType.MAVEN
    }
}
