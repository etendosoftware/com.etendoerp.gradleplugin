package com.etendoerp.gradle.jars.extractresources

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import spock.lang.TempDir
import spock.lang.Title

/**
 * This test should use the latest CORE snapshot
 */

@Title("Test to verify the correct extraction of the resources of a Etendo core jar.")
class ExtractResourcesOfCoreJarTest extends EtendoCoreResolutionSpecificationTest {

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

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST_SNAPSHOT
    }

    def "The resources of the core jar are extracted correctly"() {
        given: "A project adding the Etendo core jar dependency."
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", forceResolution : true]
        loadCore([coreType : "jar", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "jar", testProjectDir: testProjectDir])

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
