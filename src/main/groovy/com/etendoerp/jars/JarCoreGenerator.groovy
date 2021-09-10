package com.etendoerp.jars

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
                jarTask.archiveBaseName.set('etendo-core3')
                //Excluding src-gen
                jarTask.from('build/classes') {
                    exclude(PathUtils.fromPackageToPathClass(generated))
                }

                jarTask.from('build/resources') {
                    into('META-INF')
                }

            }
        }

        project.tasks.register("copyWebResources", Copy) {
            from ("${project.projectDir}/web",)
            into "${project.buildDir}/resources/web"
        }

        project.tasks.register("copyBeans", Copy) {
            from "${project.projectDir}/modules_core/org.openbravo.base.weld/config/beans.xml"
            into "${project.buildDir}/resources"
        }
        project.tasks.register("copySrcDB", Copy) {
            from "${project.projectDir}/src-db"
                exclude "**/*${FileExtensions.JAVA}"
            into "${project.buildDir}/resources/src-db"
        }

        project.tasks.register("copySrc", Copy) {
            from "${project.projectDir}/src"
            exclude "**/*${FileExtensions.JAVA}"
            into "${project.buildDir}/resources/src"
        }

        project.jar.dependsOn("jarConfig")
        project.jarConfig.dependsOn("copyWebResources", "copyBeans", "copySrcDB", "copySrc")

    }
}
