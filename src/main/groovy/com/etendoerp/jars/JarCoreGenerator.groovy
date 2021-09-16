package com.etendoerp.jars

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar

class JarCoreGenerator {
    static load(Project project) {

        project.tasks.register("jarConfig") {
            doLast {
                project.logger.info("Starting JAR configuration.")
                def jarTask = (project.jar as Jar)
                def generated = Utils.loadGeneratedEntitiesFile(project)
                jarTask.archiveBaseName.set('etendo-core')
                //Excluding src-gen
                jarTask.from('build/classes') {
                    exclude(PathUtils.fromPackageToPathClass(generated))
                }

                jarTask.from('build/resources') {
                    into('META-INF/etendo')
                }

            }
        }

        project.tasks.register("sourcesJarConfig") {
            doLast {
                project.logger.info("Starting Sources JAR configuration.")
                def sourcesJarTask = (project.sourcesJar as Jar)
                def generated = Utils.loadGeneratedEntitiesFile(project)
                sourcesJarTask.archiveBaseName.set('etendo-core')
                // Exclude .class files
                sourcesJarTask.exclude '**/*.class'

                // Exclude generated entities (src-gen)
                sourcesJarTask.from('build/classes') {
                    exclude(PathUtils.fromPackageToPathClass(generated))
                }

                sourcesJarTask.from('build/resources') {
                    into('/META-INF/etendo')
                }

                sourcesJarTask.into 'META-INF/etendo/src'
            }
        }

        project.tasks.register("copyBeans", Copy) {
            from "${project.projectDir}/modules_core/org.openbravo.base.weld/config/beans.xml"
            into "${project.buildDir}/resources"
        }

        project.tasks.register("copyLibs", Copy) {
            from "${project.projectDir}/lib"
            into "${project.buildDir}/resources/lib"
        }

        project.tasks.register("copySrcDB", Copy) {
            from "${project.projectDir}/src-db"
            exclude "**/*${FileExtensions.JAVA}"
            exclude "**/*${FileExtensions.HBM_XML}"
            exclude "**/*${FileExtensions.XSQL}"
            into "${project.buildDir}/resources/src-db"
        }

        project.tasks.register("copySrc", Copy) {
            from "${project.projectDir}/src"
            exclude "**/*${FileExtensions.JAVA}"
            exclude "**/*${FileExtensions.HBM_XML}"
            exclude "**/*${FileExtensions.XSQL}"
            into "${project.buildDir}/resources/src"
        }
        project.tasks.register("copyModules", Copy) {
            from "${project.projectDir}/modules"
            exclude "**/*${FileExtensions.JAVA}"
            exclude "**/*${FileExtensions.HBM_XML}"
            exclude "**/*${FileExtensions.XSQL}"
            into "${project.buildDir}/resources/modules"
        }

        project.tasks.register("copyModulesCore", Copy) {
            from "${project.projectDir}/modules_core"
            exclude "**/*${FileExtensions.JAVA}"
            exclude "**/*${FileExtensions.HBM_XML}"
            exclude "**/*${FileExtensions.XSQL}"
            into "${project.buildDir}/resources/modules"
        }

        project.tasks.register("copySrcJmh", Copy) {
            from "${project.projectDir}/src-jmh"
            exclude "**/*${FileExtensions.JAVA}"
            exclude "**/*${FileExtensions.HBM_XML}"
            exclude "**/*${FileExtensions.XSQL}"
            into "${project.buildDir}/resources/src-jmh"
        }

        project.tasks.register("copySrcUtil", Copy) {
            from ([
                    "${project.projectDir}/src-util/buildvalidation/build/classes",
                    "${project.projectDir}/src-util/modulescript/build/classes"
            ])
            include "**/*${FileExtensions.CLASS}"
            into "${project.buildDir}/resources/src-util"
        }

        project.tasks.register("copySrcTrl", Copy) {
            from "${project.projectDir}/src-trl/build"
            include "**/*${FileExtensions.JAR}"
            into "${project.buildDir}/resources/src-trl"
        }

        project.tasks.register("copySrcCore", Copy) {
            from "${project.projectDir}/src-core/build"
            include "**/*${FileExtensions.JAR}"
            into "${project.buildDir}/resources/src-core"
        }

        project.tasks.register("copySrcWad", Copy) {
            from "${project.projectDir}/src-wad/build"
            include "**/*${FileExtensions.JAR}"
            into "${project.buildDir}/resources/src-wad"
        }

        project.tasks.register("copyWebResources", Copy) {
            from ("${project.projectDir}/web",)
            into "${project.buildDir}/resources/web"
        }
        project.tasks.register("copyBuild", Copy) {
            from ("${project.projectDir}")
            include "build.xml"
            into "${project.buildDir}/resources"
        }

        project.tasks.register("copyReferenceData", Copy) {
            from ("${project.projectDir}/referencedata")
            into "${project.buildDir}/resources/referencedata"
        }

        project.tasks.register("copyConfig", Copy) {
            from ("${project.projectDir}/config")
            into "${project.buildDir}/resources/config"
        }

        def resourcesDirs = [
                "copyReferenceData",
                "copyConfig",
                "copyBuild",
                "copyBeans",
                "copyLibs",
                "copySrcDB",
                "copySrc",
                "copyModules",
                "copyModulesCore",
                "copySrcCore",
                "copySrcJmh",
                "copySrcTrl",
                "copySrcUtil",
                "copySrcWad",
                "copyWebResources"
        ]

        project.jar.dependsOn("jarConfig")
        project.jarConfig.dependsOn(resourcesDirs)
        project.sourcesJar.dependsOn("sourcesJarConfig")
        project.sourcesJarConfig.dependsOn(resourcesDirs)
    }
}
