package com.etendoerp.publication.configuration.pom

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

/**
 * Class used to store a subproject or normal dependency with the specified version in the build.gradle
 * This will used later to replace the 'buildGradleVersion' with the update one.
 *
 * This solves the problem of multiples subproject referencing the same subproject with different versions
 *
 * Ex:
 *  subproject A: build.gradle
 *                  |- com.test:moduleB:1.0.1
 *  subproject C: build.gradle
 *                  |- com.test.moduleB:1.0.1
 *
 */
class PomProjectContainer {

    Project projectDependency
    String buildGradleVersion
    Dependency dependency
    /**
     * Contains the artifact name 'group:artifact'
     */
    String artifactName

    String group
    String name

    /**
     * Flag used to indicate if the dependency is being publish recursively.
     *  - If 'true', the 'buildGradleVersion' should be used to replace the 'build.gradle' version
     * using the 'projectDependency' version
     */
    boolean recursivePublication = false

    boolean isProjectDependency = false

    PomProjectContainer(Project projectDependency, String buildGradleVersion) {
        this.projectDependency = projectDependency
        this.buildGradleVersion = buildGradleVersion
    }

    PomProjectContainer(Project projectDependency, String buildGradleVersion, Dependency dependency) {
        this(projectDependency, buildGradleVersion)
        this.dependency = dependency
    }

    PomProjectContainer(Dependency dependency, String artifactName, String buildGradleVersion) {
        this.dependency = dependency
        setArtifactName(artifactName)
        this.buildGradleVersion = buildGradleVersion
    }

    void setArtifactName(String artifactName) {
        this.artifactName = artifactName

        def split = artifactName.split(":")
        if (split.size() >= 2) {
            this.group = split[0]
            this.name = split[1]
        }
    }

}
