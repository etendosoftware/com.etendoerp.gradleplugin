package com.etendoerp.legacy.ant

import com.etendoerp.consistency.EtendoArtifactsConsistencyContainer
import com.etendoerp.legacy.dependencies.ResolverDependencyLoader
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel

class ConsistencyVerification {

    final static String CONSISTENCY_VERIFICATION_TASK = "consistencyVerification"

    final static String IGNORE_CONSISTENCY = "ignoreConsistency"

    static void load(Project project) {

        project.tasks.register(CONSISTENCY_VERIFICATION_TASK) {
            doLast {

                EtendoArtifactsConsistencyContainer consistencyContainer = project.ext.get(ResolverDependencyLoader.CONSISTENCY_CONTAINER)
                if (!consistencyContainer) {
                    project.logger.error("* The consistency container is not set.")
                    return
                }

                LogLevel logLevel = LogLevel.ERROR

                def ignoreConsistency = project.findProperty(IGNORE_CONSISTENCY)
                def local = System.getProperty("local")

                if (ignoreConsistency || local == "no") {
                    logLevel = LogLevel.INFO
                }

                consistencyContainer.verifyConsistency(logLevel)
            }
        }

    }

}