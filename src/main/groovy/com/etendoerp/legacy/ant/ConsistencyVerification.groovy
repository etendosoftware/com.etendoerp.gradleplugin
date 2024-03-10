package com.etendoerp.legacy.ant

import com.etendoerp.consistency.EtendoArtifactsConsistencyContainer
import com.etendoerp.legacy.dependencies.ResolverDependencyLoader
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

class ConsistencyVerification {

    final static String CONSISTENCY_VERIFICATION_TASK = 'consistencyVerification'

    final static String IGNORE_CONSISTENCY = 'ignoreConsistency'

    final static List<String> IGNORE_TASKS = [
            'install',
            'update.database',
            'create.database'
    ]

    static boolean skipConsistency(List<String> gradleTasks) {
        for (String taskName : IGNORE_TASKS) {
            if (gradleTasks.contains(taskName) || gradleTasks.contains(":${taskName}")) {
                return true
            }
        }
        return false
    }

    static void load(Project project) {

        project.tasks.register(CONSISTENCY_VERIFICATION_TASK) {
            doLast {

                // Check if the 'install' or 'update.database' is being run
                // Identify the tasks being ran
                def local = System.getProperty('local')
                if (skipConsistency(project.gradle.startParameter.taskNames) || local == 'no') {
                    project.logger.info('* Ignoring version consistency verification.')
                    return
                }

                EtendoArtifactsConsistencyContainer consistencyContainer = project.ext.get(ResolverDependencyLoader.CONSISTENCY_CONTAINER)
                if (!consistencyContainer) {
                    project.logger.info('* The consistency container is not set. Ignoring version consistency verification.')
                    return
                }

                LogLevel logLevel = LogLevel.ERROR

                // Reload the installed artifacts
                project.logger.info('* Reloading installed artifacts to run the consistency verification.')
                consistencyContainer.loadInstalledArtifacts()
                consistencyContainer.runArtifactConsistency()

                def ignoreConsistency = project.findProperty(IGNORE_CONSISTENCY)
                if (ignoreConsistency) {
                    logLevel = LogLevel.INFO
                }

                consistencyContainer.verifyConsistency(logLevel)
            }
        }

    }
}
