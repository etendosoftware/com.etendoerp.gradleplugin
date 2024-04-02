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
import org.gradle.api.logging.LogLevel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.gradle.api.file.FileTree
import org.gradle.internal.os.OperatingSystem
import org.gradle.api.logging.LogLevel
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
            List<String> dependencies = []
            List<String> files = []
            //

            /**
             * aux ant path used to hold gradle jar files
             */
            File destDirectory = new File(sourcePath, "./build/lib/runtime")
            destDirectory.mkdirs()
            jarFiles.each {
                // Copy the jar to the runtime directory
                File destFile = new File(destDirectory, it.name)
                Files.copy(it.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                files.add("./" + it.getName())
                // Add the jar to the ant path to be used to create WebContent
                dependencies.add(it.absolutePath)
            }
            def classpathJarName = "classpath.jar";
            // CREATE JAR HERE FROM gradle.custom and create a Manifest with the classpath
            project.ant.jar(destfile: "${destDirectory.absolutePath}/${classpathJarName}") {
                manifest {
                    attribute(name: "Class-Path", value: files.join(' '))
                }
            }
            project.ant.path(id:'gradle.custom')
            project.ant.references['gradle.custom'].add(project.ant.path(location: new File(destDirectory.absolutePath, classpathJarName)))

            /**
             * Creates an Ant property with the value of the gradle Jar paths.
             * Ex: '/path/to/jar0:/path/to/jar1/'
             *
             * This is used when the project loads the Ant file
             * to pass the Gradle libs classpath (dependencies defined with 'implementation').
             *
             * This is a workaround to the problem when an Ant target calls another target with '<antcall/>'
             * and the Gradle classpath is not being recognized.
             *
             * When a target uses the 'depends' value pointing to another Ant target there is no problem.
             * <antcall/> should be avoided.
             *
             * Also sometimes when Ant calls forked classes, the Ant references 'refid' defined by Gradle will be lost.
             * To prevents 'refid' errors a property with 'value' is used.
             *
             */
            project.ant.properties['gradle.custom.dependencies'] = project.ant.references['gradle.custom'].toString()

            project.ant.project.setProperty("env.GRADLE_CLASSPATH", project.ant.references['gradle.custom'].toString())

            // This gets all dependencies and sets them in ant as a file list with id: "gradle.libs"
            // Ant task build.local.context uses this to copy them to WebContent
            project.ant.filelist(id: 'gradle.libs', files: dependencies.join(','))

            AntLoader.loadAntFile(project, coreMetadata)
        }

    }

    static String getSourcePath() {
        def propsFile = new File("config/Openbravo.properties")
        def props = new Properties()
        if(propsFile.exists() == false) {
            return "."
        }
        props.load(propsFile.newReader())
        return props.getProperty("source.path")
    }
}
