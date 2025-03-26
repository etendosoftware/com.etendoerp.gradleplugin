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
                // Obtener la versión de Java actual
                def javaVersion = System.getProperty('java.version')
                def majorVersion = javaVersion.split('\\.')[0].toInteger()

                def skipVersion = project.hasProperty('java.version') ?
                        project.property('java.version').toInteger() : null

                if (skipVersion != 11) {
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
                    | 4. Optionally download and install Java 17 (or higher) from:
                    |    - https://adoptium.net/ (recommended)
                    |    - Or from Oracle's official site: https://www.oracle.com/java/
                    |
                    | Alternative (DEPRECATED - Not Recommended):
                    | If you absolutely cannot update Java, you can temporarily skip this validation
                    | by running the command with the flag: -Pjava.version=11
                    | Example: ./gradlew smartbuild -Pjava.version=11
                    | Or, you can add the following to your gradle.properties file to override this check (at your own risk):
                    |   java.version=11
                    | **WARNING**: This option is deprecated and should only be used for legacy maintenance tasks.
                    | Using this flag may lead to compatibility issues and is not supported for regular development.
                    |--------------------------------------------------
                """.stripMargin())
                    } else {
                        project.logger.info("Detected Java version: ${javaVersion}")
                    }
                } else {
                    project.logger.info("Skipping Java version check")
                }
            }
        }

        def task = project.tasks.findByName("antInit")

    }


}
