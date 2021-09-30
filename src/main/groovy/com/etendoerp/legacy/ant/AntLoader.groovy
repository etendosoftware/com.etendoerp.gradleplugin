package com.etendoerp.legacy.ant

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.ant.AntTarget

class AntLoader {

    static load(Project project) {
        /***
         * Task to check  that all configuration files exist
         * */
        project.task("compileFilesCheck"){
            doLast {
                def error = false
                if (!project.file("${project.projectDir}/gradle.properties").exists()) {
                    logger.error('No such  file ${project.projectDir}/gradle.properties')
                    error = true
                }
                if (!project.file("${project.projectDir}/config/Openbravo.properties").exists()) {
                    logger.error('No such  file ${project.projectDir}/config/Openbravo.properties')
                    error = true
                }
                if (!project.file("${project.projectDir}/config/Format.xml").exists()) {
                    logger.error('No such  file ${project.projectDir}/config/Format.xml')
                    error = true
                }
                if (!project.file("${project.projectDir}/config/log4j2.xml").exists()) {
                    logger.error('No such  file ${project.projectDir}/config/log4j2.xml')
                    error = true
                }
                if (!project.file("${project.projectDir}/config/log4j2-web.xml").exists()) {
                    logger.error('No such  file ${project.projectDir}/config/log4j2-web.xml')
                    error = true
                }

                if (error) {
                    throw new GradleException("Configuration files are missing to run this task. To fix it  modify gradle.properties file to set new configuration values, then run ./gradlew setup")
                }
            }
        }

        project.task("loadAntBuild") {
            project.afterEvaluate {
                def buildFile = new File(project.projectDir.getAbsolutePath() + File.separator + 'build' + File.separator + 'etendo' + File.separator + 'build.xml')
                def isSourceJar = false
                if (!buildFile.exists()) {
                    // TODO: instead based on the file, this condition should be available globally
                    //       based on the declared dependency
                    isSourceJar = true
                    buildFile = new File('build.xml')
                }

                /** map from ant tasks to gradle **/
                project.ant.importBuild(buildFile) { String oldTargetName ->
                    switch (oldTargetName) {
                        case 'clean':
                            return 'antClean'
                        case 'setup':
                            return 'antSetup'
                        case 'init':
                            return 'antInit'
                        case 'install.source':
                            return 'antInstall'
                        case 'war':
                            return 'antWar'
                        default:
                            if (oldTargetName.contains("test")) {
                                return "ant." + oldTargetName
                            }
                            return oldTargetName
                    }
                }

                project.ant.properties['is.source.jar'] = isSourceJar

                project.tasks.withType(AntTarget) { t ->
                    if (!isSourceJar) {
                        t.baseDir = project.file(buildFile.parent)
                    }
                }

                ['smartbuild', 'compile.complete', 'compile.complete.deploy', 'update.database', 'export.database'].each {
                    def task = project.tasks.findByName(it)
                    if (task != null) {
                        task.dependsOn(project.tasks.findByName("compileFilesCheck"))
                    }
                }

                /**
                 * This method gets all resolved dependencies by gradle and passes all resolved jars to ANT tasks
                 */

                // Note: previously the antClassLoader was used to add classes to ant's classpath
                // but when the core is a complete jar (with libs) affecting the class loader can cause collisions
                // (for example, the IOFileFilter of apache commons appears in the core jar and the gradle libs)
                // Since the defined ant tasks specify a classpath, the code below should be enough
                // otherwise doing a antClassLoader.addURL for each dependency will bring back the previous behaviour, but it will cause problems
                // see https://github.com/gradle/gradle/issues/11914 for more info
                def antClassLoader = org.apache.tools.ant.Project.class.classLoader
                URL[] antClasses = antClassLoader.getURLs()

                def newPath = []
                def dependencies = []

                project.configurations.compile.collect {

                    //antClassLoader.addURL it.toURL()

                    newPath.add project.ant.path(location: it)
                    dependencies.add project.ant.path(location: it)

                    project.ant.properties['testFromGradle'] = it.getAbsolutePath()

                    project.logger.log(LogLevel.INFO, "Gradle library " + it.getName() + " added to gradle.libs ant filelist")
                }

                // This method gets all compile dependencies and sets them in ant as a file list with id: "gradle.libs"
                // Ant task build.local.context uses this to copy them to WebContent
                project.ant.filelist(id: 'gradle.libs', files: dependencies.join(','))

                project.ant.references.keySet().forEach {
                    if (it.contains("path")) {
                        newPath.forEach { pth ->
                            project.logger.log(LogLevel.INFO, "ant reference " + it + " add to classpath " + pth)
                            project.ant.references[it].add(pth)
                        }
                    }
                }

                /** Call ant setup to prepare environment */
                project.task("setup") {
                    dependsOn project.loadAntBuild
                    ant.properties['nonInteractive'] = true
                    ant.properties['acceptLicense'] = true
                    project.tasks.findByName('antSetup').mustRunAfter'prepareConfig'
                    finalizedBy(project.tasks.findByName("prepareConfig"), project.tasks.findByName("antSetup"))
                }

                /** The install.source ant task now depends on ant setup */
                project.task("install") {
                    dependsOn project.loadAntBuild
                    boolean doSetup = project.hasProperty("doSetup") ? doSetup.toBoolean() : true
                    // Do not depend on setup if specified with -PdoSetup=false
                    if (doSetup) {
                        dependsOn project.tasks.findByName("setup")
                    }
                    dependsOn project.tasks.findByName("antInstall")
                }
            }
        }

    }

}
