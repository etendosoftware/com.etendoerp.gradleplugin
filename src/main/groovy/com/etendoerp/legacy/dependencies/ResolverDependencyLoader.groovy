package com.etendoerp.legacy.dependencies

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

            def antClassLoader = org.apache.tools.ant.Project.class.classLoader
            def newPath = []
            //
            jarFiles.each {
                antClassLoader.addURL it.toURL()
            }
            jarFiles.each {
                newPath.add project.ant.path(location: it)
            }
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