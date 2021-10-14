package com.etendoerp.gradle.jars.extractresources

import com.etendoerp.gradle.jars.JarsUtils
import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("Test to verify the correct extraction of the resources of a Etendo core jar.")
@Narrative(""" TODO: Currently this test will fail because the gradle ant class loader is adding the Etendo core library.
This causes problems because the 'Etendo core' contains classes that are already defined in the 'Gradle project'

The test sets a Etendo core dependency and runs the 'dependency' task to trigger the 'extractResourcesOfJar' task.
""")
class ExtractResourcesOfCoreJarTest extends EtendoSpecification {

    final static String ETENDO_CORE_GROUP   = "com.etendoerp.platformtest"
    final static String ETENDO_CORE_NAME    = "etendo-core-test"
    final static String ETENDO_CORE_VERSION = "1.0.0"
    final static String ETENDO_CORE_REPO    = "https://repo.futit.cloud/repository/etendo-test-core/"

    final static List<String> CORE_FILES = [
            "src",
            "src-db",
            "web",
            "config",
            "referencedata",
            "modules",
            // TODO: Check that the jar contains the 'build.xml' file
            //"build.xml"
    ]

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    def "The resources of the core jar are extracted correctly"() {
        given: "A project adding the Etendo core jar dependency."
        def core = "${ETENDO_CORE_GROUP}:${ETENDO_CORE_NAME}:${ETENDO_CORE_VERSION}"
        buildFile << JarsUtils.generateDependenciesBlock([core])

        and: "The project contains the core repository"
        buildFile << """
        repositories {
            maven {
                url "${ETENDO_CORE_REPO}"
            }
        }
        """

        when: "The users runs the 'dependencies' task"
        def dependenciesTaskResult = runTask(":dependencies","-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")

        then: "The task will finish successfully."
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS

        and: "The output will contain the core dependency."
        dependenciesTaskResult.output.contains(core)

        and: "The resources of the Etendo core will be extracted in the 'build/etendo' directory"
        def location = new File("${getProjectDir().absolutePath}/build/etendo")
        containsAllCoreFiles(location)
    }

    static void containsAllCoreFiles(File coreLocation) {
        def listFiles = coreLocation.list()
        CORE_FILES.each {coreFile ->
            assert listFiles.find {it == coreFile} != null
        }
    }
}
