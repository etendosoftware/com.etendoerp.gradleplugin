package com.etendoerp.legacy.dependencies

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.consistency.EtendoArtifactsConsistencyContainer
import com.etendoerp.core.CoreMetadata
import com.etendoerp.dependencies.EtendoCoreDependencies
import com.etendoerp.legacy.ant.AntLoader
import com.etendoerp.legacy.utils.GithubUtils
import com.etendoerp.modules.ModulesConfigurationUtils
import com.etendoerp.publication.configuration.PublicationConfiguration
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.internal.os.OperatingSystem


class ResolverDependencyLoader {

    final static String CONSISTENCY_CONTAINER = "CONSISTENCY_CONTAINER"

    static load(Project project) {

        // Configuration container used to store all project and subproject dependencies
        project.configurations {
            etendoDependencyContainer
        }

        /**
         * This method gets all resolved dependencies by gradle and pass all resolved jars to ANT tasks
         */

        project.afterEvaluate {
            project.logger.info("Running GRADLE projectsEvaluated.")

            if (project.state.failure) {
                project.logger.error("* ERROR: ${project.state.failure.getMessage()}")
                project.state.failure.printStackTrace()
                return
            }

            GithubUtils.configureRepositories(project)
            CoreMetadata coreMetadata = new CoreMetadata(project)

            ModulesConfigurationUtils.configureSubprojects(project)

            PublicationConfiguration publicationConfiguration = new PublicationConfiguration(project)
            publicationConfiguration.configurePublication()

            EtendoArtifactsConsistencyContainer consistencyContainer = new EtendoArtifactsConsistencyContainer(project, coreMetadata)
            consistencyContainer.loadInstalledArtifacts()

            // Save the consistency container in the project
            project.ext.set(CONSISTENCY_CONTAINER, consistencyContainer)

            def extension = project.extensions.findByType(EtendoPluginExtension)
            boolean loadCompilationDependencies = extension.loadCompilationDependencies
            boolean loadTestDependencies = extension.loadTestDependencies

            // Load Etendo core compilation dependencies when the core is in jar
            if (loadCompilationDependencies) {
                EtendoCoreDependencies.loadCoreCompilationDependencies(project)
            }

            // Load Etendo core test dependencies
            if (loadTestDependencies) {
                EtendoCoreDependencies.loadCoreTestDependencies(project)
            }

            DependencyProcessor dependencyProcessor = new DependencyProcessor(project, coreMetadata)
            List<File> jarFiles = dependencyProcessor.processJarFiles()

            // Note: previously the antClassLoader was used to add classes to ant's classpath
            // but when the core is a complete jar (with libs) affecting the class loader can cause collisions
            // (for example, the IOFileFilter of apache commons appears in the core jar and the gradle libs)
            // Since the defined ant tasks specify a classpath, the code below should be enough
            // otherwise doing a antClassLoader.addURL for each dependency will bring back the previous behaviour, but it will cause problems
            // see https://github.com/gradle/gradle/issues/11914 for more info
            def antClassLoader = org.apache.tools.ant.Project.class.classLoader
            def dependencies = []
            //

            /**
             * aux ant path used to hold gradle jar files
             */
            File destDirectory = new File(project.buildDir, "lib")
            destDirectory.mkdirs()
            jarFiles.each {
                // Copy the jar to the runtime directory
                dependencies.add(it.absolutePath)
            }
            def files = dependencies
            def DIRS = [
                    new File("${project.rootDir.absolutePath}/lib"),
                    new File("${project.rootDir.absolutePath}/modules"),
                    new File("${project.rootDir.absolutePath}/modules_core")
            ]
            DIRS.each {
                if (it.exists()) {
                    FileTree libFiles = project.fileTree(it).include("**/*.jar")
                    // Search recursively for all jars in the lib directory and add to classpath jar
                    libFiles.each {
                        files.add(it)
                    }
                }
            }
            def classpathJarName = "classpath.jar"
            def classpathJarFullPath = "${destDirectory.absolutePath}/${classpathJarName}"
            def strClasspath = ""
            files.forEach {
                if (OperatingSystem.current().isWindows()) {
                    // Windows paths need to be file:/// and replace \ with /
                    strClasspath += "file:///" + it.toString().replaceAll("\\\\", "/") + " "
                } else {
                    strClasspath += it.toString() + " "
                }
            }
            // CREATE JAR HERE FROM gradle.custom and create a Manifest with the classpath
            project.ant.jar(destfile: "${classpathJarFullPath}") {
                manifest {
                    attribute(name: "Class-Path", value: strClasspath)
                }
            }
            project.ant.property(name: "base.lib", location: new File("${project.rootDir}/build", "lib"))
            //
            project.ant.path(id:'gradle.custom')
            project.ant.references['gradle.custom'].add(project.ant.path(location: classpathJarFullPath))
            project.ant.properties['gradle.custom.dependencies'] = project.ant.references['gradle.custom'].toString()
            project.ant.project.setProperty("env.GRADLE_CLASSPATH", project.ant.references['gradle.custom'].toString())

            // This gets all dependencies and sets them in ant as a file list with id: "gradle.libs"
            // Ant task build.local.context uses this to copy them to WebContent
            project.ant.filelist(id: 'gradle.libs', files: dependencies.join(','))

            AntLoader.loadAntFile(project, coreMetadata)
        }

    }

}
