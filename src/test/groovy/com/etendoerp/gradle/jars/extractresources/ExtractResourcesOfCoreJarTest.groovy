package com.etendoerp.gradle.jars.extractresources

import com.etendoerp.gradle.jars.EtendoCoreJarSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

/**
 * This test should use the latest CORE snapshot
 *  // TODO: This test should resolve from EtendoCoreResolutionSpecificationTest
 // TODO: Use latest snapshot
 */

@Title("Test to verify the correct extraction of the resources of a Etendo core jar.")
@Narrative(""" TODO: Currently this test will fail because the gradle ant class loader is adding the Etendo core library.
This causes problems because the 'Etendo core' contains classes that are already defined in the 'Gradle project'
""")
class ExtractResourcesOfCoreJarTest extends EtendoCoreJarSpecificationTest {

    final static List<String> CORE_FILES = [
            "src",
            "src-db",
            "web",
            "config",
            "referencedata",
            "modules",
            "build.xml"
    ]

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    def "The resources of the core jar are extracted correctly"() {
        given: "A project adding the Etendo core jar dependency."
        assert buildFile.text.contains(CORE)

        when: "The users runs the 'dependencies' task"
        def dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies","-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")

        then: "The task will finish successfully."
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS

        and: "The output will contain the core dependency."
        dependenciesTaskResult.output.contains(CORE)

        and: "The resources of the Etendo core will be extracted in the 'build/etendo' directory"
        def location = new File("${getProjectDir().absolutePath}/build/etendo")
        containsAllCoreFiles(location)
    }

    static void containsAllCoreFiles(File coreLocation) {
        CORE_FILES.each {coreFile ->
            assert new File("${coreLocation.absolutePath}/${coreFile}").exists()
        }
    }
}
