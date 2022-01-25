package com.etendoerp.legacy.ant

import com.etendoerp.jars.JarCoreGenerator
import com.etendoerp.jars.modules.metadata.DependencyUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
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

    static void loadAntFile(Project project) {
        def buildFile = new File(project.projectDir.getAbsolutePath() + File.separator + 'build' + File.separator + 'etendo' + File.separator + 'build.xml')
        def isSourceJar = false
        if (!buildFile.exists()) {
            // TODO: instead based on the file, this condition should be available globally
            //       based on the declared dependency
            isSourceJar = true
            buildFile = new File('build.xml')
        }

        project.ant.properties['is.source.jar'] = isSourceJar

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
