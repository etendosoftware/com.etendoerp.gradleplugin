package com.etendoerp.gradle.jars.configuration

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import spock.lang.TempDir
import spock.lang.Title

/**
 * This test should use the latest CORE snapshot
 */

@Title("Test to verify that the config phase copies the 'config' dir to the root project.")
class CopyConfigDirFromEtendoCoreJarTest extends EtendoCoreResolutionSpecificationTest {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST_SNAPSHOT
    }

    public final static List<String> CONFIG_FILES = [
            "Openbravo.properties.template",
            "backup.properties.template",
            "Format.xml.template",
            "log4j2.xml.template",
            "log4j2-web.xml.template"
    ]

    def "Adding the Etendo core dependency JAR copies the config folder to the root project"() {
        given: "A Project with the Etendo core jar"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", forceResolution : true]
        loadCore([coreType : "jar", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "jar", testProjectDir: testProjectDir])

        expect: "The config dir to be copied in the root project"
        def configDir = new File("${getProjectDir().absolutePath}/config")
        assert configDir.exists()

        and: "The config dir contains all the required files"
        containsConfigFiles(configDir)
    }

    static void containsConfigFiles(File configDir) {
        def listFiles = configDir.list()
        CONFIG_FILES.each {coreFile ->
            assert listFiles.find {it == coreFile} != null
        }
    }
}
