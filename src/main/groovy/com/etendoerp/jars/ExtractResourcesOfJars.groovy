package com.etendoerp.jars

import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project

class ExtractResourcesOfJars {

    /**
     * Extract all the resources of the JAR files which contains 'META-INF/etendo' directory
     * @param project
     */
    static void extractResources(Project project) {
        project.copy {
            from {
                project.configurations.findByName(PublicationUtils.ETENDO_DEPENDENCY_CONTAINER).findResults {
                    project.zipTree(it).matching { 
                        include 'META-INF/etendo/' 
                        include 'META-INF/build.xml'
                    }
                }
            }
            into "${project.buildDir}/etendo"

            //Deleting path prefix for each extracted file
            eachFile { f ->
                if (f.path == 'META-INF/build.xml') {
                    f.path = 'META-INF/etendo/build.xml'
                } else {
                    f.path = f.path.replaceFirst('META-INF/etendo/', '')
                }
            }
            includeEmptyDirs false
        }
    }

    /**
     * Copies the 'config' files located in the 'build/etendo/config' dir to the
     * root project. The copy is performed only if the 'config' dir
     * does not exists in the root project.
     * @param project
     */
    static void copyConfigFile(Project project) {
        def etendoConfigLocation = project.file("${project.buildDir}/etendo/config")
        def rootConfigLocation   = project.file("${project.rootDir}/config")

        if (etendoConfigLocation.exists() && !rootConfigLocation.exists()) {
            project.logger.info("Copying 'etendo/config' file to the root project.")
            project.copy {
                from(project.file("${project.buildDir}/etendo")) {
                    include("config/**")
                }
                into project.rootDir
            }
        }
    }
}

