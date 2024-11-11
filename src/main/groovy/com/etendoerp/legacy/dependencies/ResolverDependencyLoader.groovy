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
      List<String> dependencies = []
      /**
       * aux ant path used to hold gradle jar files
       */
      final String LIB_DIR = 'lib'
      File destDirectory = new File(project.buildDir, LIB_DIR)
      destDirectory.mkdirs()
      jarFiles.each { File jarFile ->
        // Copy the jar to the runtime directory
        dependencies.add(jarFile.absolutePath)
      }
      List<String> files = dependencies
      final List<File> DIRS = [
              new File("${project.rootDir.absolutePath}/lib"),
              new File("${project.rootDir.absolutePath}/modules"),
              new File("${project.rootDir.absolutePath}/modules_core"),
      ]
      DIRS.each { File dir ->
        if (dir.exists()) {
          FileTree libFiles = project.fileTree(dir).include('**/*.jar') as FileTree
          // Search recursively for all jars in the lib directory and add to classpath jar
          libFiles.each { File lib ->
            files.add(lib.absolutePath)
          }
        }
      }
      final String CLASSPATH_JAR_NAME = 'classpath.jar'
      final String CLASSPATH_JAR_ABSOLUTE_PATH = "${destDirectory.absolutePath}/${CLASSPATH_JAR_NAME}"
      final String CLASSPATH_SEPARATOR = ' '
      String strClasspath = ''
      files.forEach { String file ->
        if (OperatingSystem.current().isWindows()) {
          // Windows paths need to be file:/// and replace \ with /
          strClasspath += 'file:///' + file.toString().replaceAll('\\\\', '/') + CLASSPATH_SEPARATOR
        } else {
          strClasspath += file.toString() + CLASSPATH_SEPARATOR
        }
      }
      // CREATE JAR HERE FROM gradle.custom and create a Manifest with the classpath
      project.ant.jar(destfile: CLASSPATH_JAR_ABSOLUTE_PATH) {
        manifest {
          attribute(name: 'Class-Path', value: strClasspath)
        }
      }
      project.ant.property(name: 'base.lib', location: new File("${project.rootDir}/build", LIB_DIR))
      //
      final String GRADE_CUSTOM = 'gradle.custom'
      project.ant.path(id: GRADE_CUSTOM)
      project.ant.references[GRADE_CUSTOM].add(project.ant.path(location: CLASSPATH_JAR_ABSOLUTE_PATH))
      project.ant.properties['gradle.custom.dependencies'] = project.ant.references[GRADE_CUSTOM].toString()
      project.ant.project.setProperty('env.GRADLE_CLASSPATH', project.ant.references[GRADE_CUSTOM].toString())

      // This gets all dependencies and sets them in ant as a file list with id: "gradle.libs"
      // Ant task build.local.context uses this to copy them to WebContent
      project.ant.filelist(id: 'gradle.libs', files: dependencies.join(','))

      AntLoader.loadAntFile(project, coreMetadata)
    }

  }

}
