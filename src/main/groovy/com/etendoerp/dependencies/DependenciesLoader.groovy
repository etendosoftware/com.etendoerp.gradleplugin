package com.etendoerp.dependencies

import com.etendoerp.dependencies.processor.DependenciesProcessor
import org.gradle.api.Project

class DependenciesLoader {
    public static String REPOSITORY_TO_PUBLISH = "etendo-public-jars"
    public static String URL_TO_PUBLISH        = "https://repo.futit.cloud/repository/"

    public final static String REPOSITORY_PARAMETER  = "repo"
    public final static String LOCATION_PARAMETER    = "location"
    public final static String DESTINATION_PARAMETER = "destination"

    static void load(Project project) {
        project.tasks.register("searchJarDependency") {
            doLast {
                String urlToPublish        = URL_TO_PUBLISH
                String repositoryToPublish = REPOSITORY_TO_PUBLISH

                // Get user repository
                String repoParameter = project.findProperty(REPOSITORY_PARAMETER)
                if (repoParameter) {
                    repositoryToPublish = repoParameter
                }
                urlToPublish = urlToPublish + repositoryToPublish

                // Default location to search jars files
                def locationToSearch = project.rootDir
                String locationParameter = project.findProperty(LOCATION_PARAMETER)
                if (locationParameter) {
                    locationToSearch = project.file(locationParameter)
                }

                // Get destination files
                def defaultDestination = project.rootDir
                String destinationParameter = project.findProperty(DESTINATION_PARAMETER)
                if (destinationParameter) {
                    defaultDestination = new File(defaultDestination, destinationParameter)
                }

                def dependenciesProcessor = new DependenciesProcessor(project, locationToSearch, defaultDestination, repositoryToPublish, urlToPublish)
                dependenciesProcessor.process()
            }
        }
    }

}
