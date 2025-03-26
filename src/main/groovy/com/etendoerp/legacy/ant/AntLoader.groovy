package com.etendoerp.legacy.ant

import com.etendoerp.core.CoreMetadata
import com.etendoerp.core.CoreType
import com.etendoerp.jars.JarCoreGenerator
import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.java.JavaCheckLoader
import com.etendoerp.legacy.ant.compilejava.CompileJavaLoader
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.tasks.ant.AntTarget

class AntLoader {

    static load(Project project) {

        ConsistencyVerification.load(project)
        CompileJavaLoader.load(project)

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
        }

    }

    /**
     * The core is in sources if 'etendo-core' dependency does not exists..
     * @param project
     * @return
     */
    static boolean isCoreInSources(Project project) {
        def modulesCoreLocation = project.file("modules_core")
        def srcCoreLocation = project.file("src-core")

        if (modulesCoreLocation.exists() && srcCoreLocation.exists()) {
            return true
        }

        // Search if the core is in JARs using the dependencies
        def baseProjectConfigurations = DependencyUtils.loadListOfConfigurations(project)
        for (Configuration configuration : baseProjectConfigurations) {
            for (Dependency dependency : configuration.allDependencies) {
                if (dependency.name == JarCoreGenerator.ETENDO_CORE) {
                    return false
                }
            }
        }

        return true
    }

    static void loadAntFile(Project project, CoreMetadata coreMetadata) {
        File buildFile = null
        def coreInSources = false

        if (coreMetadata.coreType == CoreType.UNDEFINED) {
            project.logger.info("The ant file 'build.xml' could not be loaded because the Etendo core is not defined.")
            return
        }

        if (coreMetadata.coreType == CoreType.SOURCES) {
            buildFile = new File(project.rootDir, 'build.xml')
            project.logger.info("*********************************************")
            project.logger.info("* Core in SOURCES - Reading 'build.xml' from '${buildFile.absolutePath}'")
            project.logger.info("*********************************************")
            coreInSources = true
        } else if (coreMetadata.coreType == CoreType.JAR) {
            buildFile = new File(project.buildDir, "etendo" + File.separator + "build.xml")
            project.logger.info("*********************************************")
            project.logger.info("* Core in JARs - Reading 'build.xml' from ${buildFile.absolutePath}")
            project.logger.info("*********************************************")
            coreInSources = false
        }

        if (!buildFile || !buildFile.exists()) {
            project.logger.error("* The 'build.xml' file does not exists.")
            return
        }

        project.ant.properties['is.source.jar'] = coreInSources

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

        /**
         * The cleanSubFolders task is running after the core.lib task
         */
        project.tasks.findByName("core.lib")?.mustRunAfter("cleanSubfolders")

        project.ant.properties['is.source.jar'] = coreInSources

        project.tasks.withType(AntTarget) { t ->
            if (!coreInSources) {
                t.baseDir = project.file(buildFile.parent)
            }
        }

        ['smartbuild', 'compile.complete', 'compile.complete.deploy', 'update.database', 'export.database'].each {
            def task = project.tasks.findByName(it)
            if (task != null) {
                task.dependsOn(project.tasks.findByName("compileFilesCheck"))
            }
        }

        // Consistency verification
        ['smartbuild', 'compile.complete', 'compile.complete.deploy'].each {
            def task = project.tasks.findByName(it)
            if (task != null) {
                task.dependsOn(project.tasks.findByName(ConsistencyVerification.CONSISTENCY_VERIFICATION_TASK))
            }
        }

        // Dependencies sync
        def depSync = project.tasks.findByName("dependencies.sync")
        if (depSync != null) {
            ['smartbuild', 'compile.complete', 'compile.complete.deploy', 'update.database', 'export.database', 'expandModules'].each {
                def task = project.tasks.findByName(it)
                if (task != null) {
                    task.dependsOn(depSync)
                }
            }
        }

        // Adding java dummy task to prevent deleting 'build/classes' dir when
        // the task 'compileJava' is executed for first time.
        def antInitTask = project.tasks.findByName("antInit")
        def compileJavaDummy = project.tasks.findByName(CompileJavaLoader.TASK_NAME)
        if (antInitTask != null && compileJavaDummy != null) {
            antInitTask.dependsOn(compileJavaDummy)
        }
        def javaCheckTask = project.tasks.findByName(JavaCheckLoader.TASK_NAME)
        if (antInitTask != null && javaCheckTask != null) {
            antInitTask.dependsOn(javaCheckTask)
        }
        /** Call ant setup to prepare environment */
        project.task("setup") {
            ant.properties['nonInteractive'] = true
            ant.properties['acceptLicense'] = true
            project.tasks.findByName('antSetup').mustRunAfter'prepareConfig'
            finalizedBy(project.tasks.findByName("prepareConfig"), project.tasks.findByName("antSetup"))
        }

        /** The install.source ant task now depends on ant setup */
        project.task("install") {
            boolean doSetup = project.hasProperty("doSetup") ? doSetup.toBoolean() : true
            // Do not depend on setup if specified with -PdoSetup=false
            if (doSetup) {
                dependsOn project.tasks.findByName("setup")
            }
            dependsOn project.tasks.findByName("antInstall")
        }

    }

}
