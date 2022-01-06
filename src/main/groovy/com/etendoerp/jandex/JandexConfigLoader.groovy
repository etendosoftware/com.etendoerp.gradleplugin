package com.etendoerp.jandex

import org.gradle.api.Project

/**
 * Class used to config the Jandex plugin.
 * A new sourceSet is need it to only compile the files that will be package in the Etendo core Jar.
 * Jandex will use this sourceSet to create the index file.
 */
class JandexConfigLoader {

    static void load(Project project) {

        def jandexTask = project.tasks.findByName("jandex")

        if (!jandexTask) {
            project.logger.info("The jandex task does not exists. Ignoring creation of jandex index.")
            return
        }

        /**
         * Creation of a custom sourceSets to contain only the .class files
         * that will be included in the Etendo core Jar.
         *
         * This sourceSet will be used by the jandex task to generate the bean index.
         */
        project.sourceSets {
            jandexCustom {
                java {
                    outputDir = project.file("${project.buildDir}/etendo-jandex-classes/")
                    srcDirs = ['build/javasqlc/src']
                    srcDirs 'src'
                }
                compileClasspath += project.sourceSets.main.runtimeClasspath
                runtimeClasspath += project.sourceSets.main.runtimeClasspath
            }
        }

        //set the modules_core sources directories.
        if (project.file('modules_core').exists() && project.file('modules_core').isDirectory()) {
            project.file('modules_core').eachDir {
                def moduleSrcDir = new File(it, "src")
                if (moduleSrcDir.exists() && moduleSrcDir.isDirectory()) {
                    project.logger.info("Adding '${moduleSrcDir.toString()}' to jandex sourceSets.")
                    project.sourceSets.jandexCustom?.java?.srcDirs += moduleSrcDir.toString()
                }
            }
        }

        /**
         * Jandex is used to index the .class files containing annotations.
         * This generates a file called 'jandex.inx', and is stored in the 'META-INF' dir of the core jar.
         * Weld uses this file to scan all the beans when tomcat is started.
         */
        project.jandex {
            includeInJar true
            processDefaultFileSet false
            sources.from(project.sourceSets.jandexCustom.output.classesDirs)
        }

    }
}
