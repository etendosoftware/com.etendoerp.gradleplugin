package com.etendoerp.jars

import org.gradle.api.Project
import org.gradle.api.tasks.Copy


class ExtractResourcesOfJars {
    static load(Project project) {

        project.tasks.register("extractResourcesOfJar", Copy) {
            from {
                project.configurations.compile.findResults {
                    project.zipTree(it).matching { include 'META-INF/etendo/' }

                }
            }
            into "${project.buildDir}/etendo"

            //Deleting path prefix for each extracted file
            eachFile { f ->
                f.path = f.path.replaceFirst( 'META-INF/etendo/', '')
            }
            includeEmptyDirs false
        }

        project.tasks.matching {it != project.extractResourcesOfJar}.all {it.dependsOn project.extractResourcesOfJar}

    }
}

