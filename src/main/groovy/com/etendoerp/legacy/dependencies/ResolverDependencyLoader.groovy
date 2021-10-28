package com.etendoerp.legacy.dependencies

import com.etendoerp.jars.ExtractResourcesOfJars
import com.etendoerp.legacy.utils.NexusUtils
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

class ResolverDependencyLoader {

    static load(Project project) {

        // Configuration container used to store all project and subproject dependencies
        project.configurations {
            etendoDependencyContainer
        }

        /**
         * This method gets all resolved dependencies by gradle and pass all resolved jars to ANT tasks
         */

        project.gradle.projectsEvaluated {
            project.logger.info("Running GRADLE projectsEvaluated.")

            NexusUtils.configureRepositories(project)

            List<File> jarFiles = ResolverDependencyUtils.getJarFiles(project)

            ExtractResourcesOfJars.extractResources(project)
            ExtractResourcesOfJars.copyConfigFile(project)

            // Note: previously the antClassLoader was used to add classes to ant's classpath
            // but when the core is a complete jar (with libs) affecting the class loader can cause collisions
            // (for example, the IOFileFilter of apache commons appears in the core jar and the gradle libs)
            // Since the defined ant tasks specify a classpath, the code below should be enough
            // otherwise doing a antClassLoader.addURL for each dependency will bring back the previous behaviour, but it will cause problems
            // see https://github.com/gradle/gradle/issues/11914 for more info
            def antClassLoader = org.apache.tools.ant.Project.class.classLoader
            def newPath = []
            def dependencies = []
            //
            jarFiles.each {
                newPath.add project.ant.path(location: it)
                dependencies.add project.ant.path(location: it)
            }

            // This gets all dependencies and sets them in ant as a file list with id: "gradle.libs"
            // Ant task build.local.context uses this to copy them to WebContent
            project.ant.filelist(id: 'gradle.libs', files: dependencies.join(','))

            //
            project.ant.references.keySet().forEach {
                if(it.contains("path")) {
                    newPath.forEach { pth ->
                        project.logger.log(LogLevel.INFO, "GRADLE - ant reference " + it + " add to classpath " + pth)
                        project.ant.references[it].add(pth)
                    }
                }
            }

        }

    }
}
