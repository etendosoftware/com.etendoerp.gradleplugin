package com.etendoerp.publication.configuration.pom

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

/**
 * Class used to store a subproject dependency with the specified version in the build.gradle
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

    PomProjectContainer(Project projectDependency, String buildGradleVersion) {
        this.projectDependency = projectDependency
        this.buildGradleVersion = buildGradleVersion
    }

    PomProjectContainer(Project projectDependency, String buildGradleVersion, Dependency dependency) {
        this(projectDependency, buildGradleVersion)
        this.dependency = dependency
    }

}
