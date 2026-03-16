package com.etendoerp.java

import org.gradle.api.GradleException
import org.gradle.api.Project

class JavaCheckLoader {

    static final String TASK_NAME = "checkJavaVersion"

    static load(Project project) {
        project.tasks.register(TASK_NAME) {
            group = 'verification'
            description = 'Verifies current Java version'

            doLast {
                def javaVersion = System.getProperty('java.version')
                def majorVersion = javaVersion.split('\\.')[0].toInteger()

                if (majorVersion < 17) {
                    throw new GradleException("""
                    |--------------------------------------------------
                    | ERROR: Incompatible Java Version
                    |--------------------------------------------------
                    | The detected Java version (${javaVersion}) does not meet the minimum requirement.
                    | **Java 17 or higher is required** to build Etendo.
                    |
                    | How to fix this?
                    | 1. Check your current Java version by running: java -version
                    | 2. Use a version manager like SDKMAN! (https://sdkman.io/)
                    |    to manage multiple Java versions easily.
                    | 3. Update your JAVA_HOME environment variable to point to the new version.
                    | 4. Download and install Java 17 (or higher) from:
                    |    - https://adoptium.net/ (recommended)
                    |    - Or from Oracle's official site: https://www.oracle.com/java/
                    |--------------------------------------------------
                """.stripMargin())
                } else {
                    project.logger.info("Detected Java version: ${javaVersion}")
                }
            }
        }

        def task = project.tasks.findByName("antInit")

    }


}
