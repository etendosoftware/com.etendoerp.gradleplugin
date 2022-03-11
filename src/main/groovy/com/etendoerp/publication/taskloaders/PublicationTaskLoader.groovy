package com.etendoerp.publication.taskloaders

import org.gradle.api.Project

// Class used to load the tasks need it to publish a subproject
class PublicationTaskLoader {

    static void load(Project mainProject, Project subProject) {
        // Configure ZIP task
        ZipTaskGenerator.load(mainProject, subProject)

        // Configure JAR task
        JarTaskGenerator.load(mainProject, subProject)

        // Configure MAVEN publication task
        MavenTaskGenerator.load(mainProject, subProject)

        // Load the main publish task
        PublishTaskGenerator.load(mainProject, subProject)
    }
}
