package com.etendoerp.jars

import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar

class JarCoreGenerator {

    public static final String RESOURCES_DIR = 'build/resources'
    public static final String RESOURCES_JAR_DESTINATION = 'META-INF/'
    public static final String BUILD_CLASES = 'build/classes'
    public static final String ETENDO_CORE = 'etendo-core'

    static load(Project project) {

        project.tasks.register("jarConfig") {
            doLast {
                project.logger.info("Starting JAR configuration.")
                def jarTask = (project.jar as Jar)
                def generated = Utils.loadGeneratedEntitiesFile(project)

                jarTask.archiveBaseName.set(ETENDO_CORE)
                //Excluding src-gen
                jarTask.from(BUILD_CLASES) {
                    exclude(PathUtils.fromPackageToPathClass(generated))
                }

                jarTask.from(RESOURCES_DIR) {
                    into(RESOURCES_JAR_DESTINATION)
                }

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
            into "${project.buildDir}/resources/etendo/src-db"
        }

        project.tasks.register("copySrc", Copy) {
            from "${project.projectDir}/src"
            exclude "**/*${FileExtensions.JAVA}"
            exclude "**/*${FileExtensions.HBM_XML}"
            exclude "**/*${FileExtensions.XSQL}"
            into "${project.buildDir}/resources/etendo/src"
        }
        project.tasks.register("copyModules", Copy) {
            from "${project.projectDir}/modules"
            exclude "**/*${FileExtensions.JAVA}"
            exclude "**/*${FileExtensions.HBM_XML}"
            exclude "**/*${FileExtensions.XSQL}"
            into "${project.buildDir}/resources/etendo/modules"
        }

        project.tasks.register("copyModulesCore", Copy) {
            from "${project.projectDir}/modules_core"
            exclude "**/*${FileExtensions.JAVA}"
            exclude "**/*${FileExtensions.HBM_XML}"
            exclude "**/*${FileExtensions.XSQL}"
            into "${project.buildDir}/resources/etendo/modules"
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
            from "${project.projectDir}/src-trl/lib"
            include "**/*${FileExtensions.JAR}"
            into "${project.buildDir}/resources/src-trl"
        }

        project.tasks.register("copySrcCore", Copy) {
            from "${project.projectDir}/src-core/lib"
            include "**/*${FileExtensions.JAR}"
            into "${project.buildDir}/resources/src-core"
        }

        project.tasks.register("copySrcWad", Copy) {
            from "${project.projectDir}/src-wad/lib"
            include "**/*${FileExtensions.JAR}"
            into "${project.buildDir}/resources/src-wad"
        }

        project.tasks.register("copyWebResources", Copy) {
            from ("${project.projectDir}/web",)
            into "${project.buildDir}/resources/etendo/web"
        }
        project.tasks.register("copyBuild", Copy) {
            from ("${project.projectDir}")
            include "build.xml"
            into "${project.buildDir}/resources"
        }

        project.tasks.register("copyReferenceData", Copy) {
            from ("${project.projectDir}/referencedata")
            into "${project.buildDir}/resources/etendo/referencedata"
        }

        project.tasks.register("copyConfig", Copy) {
            from ("${project.projectDir}/config")
            into "${project.buildDir}/resources/etendo/config"
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
    }
}
