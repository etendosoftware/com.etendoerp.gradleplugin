package com.etendoerp.jars

import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.tasks.Copy


class ExtractResourcesOfJars {
    static load(Project project) {

        project.tasks.register("extractResourcesOfJar", Copy) {
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

        project.tasks.matching {it != project.extractResourcesOfJar}.all {it.dependsOn project.extractResourcesOfJar}

    }
}

