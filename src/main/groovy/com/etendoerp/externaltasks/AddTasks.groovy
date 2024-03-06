package com.etendoerp.externaltasks

import org.gradle.api.Project

class AddTasks {
    static void load(Project project) {
        project.afterEvaluate {
            File sources = new File("${project.rootDir.path + File.separator}modules")
            File jars = new File("${project.buildDir.path + File.separator}etendo${File.separator}modules")

            if (sources.exists()) {
                project.fileTree(dir: sources).matching {
                    include '**/tasks.gradle'
                }.each { fileSrc ->
                    project.apply from: fileSrc.path
                }
            }

            if (jars.exists()) {
                project.fileTree(dir: jars).matching {
                    include '**/tasks.gradle'
                }.each { fileJar ->
                    project.apply from: fileJar.path
                }
            }
        }
    }
}
