package com.etendoerp.gradle.jars.configuration

import com.etendoerp.gradle.jars.EtendoCoreJarSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("Test to verify that the config phase copies the 'config' dir to the root project.")
@Narrative("""TODO: Currently this test will fail because the gradle ant class loader is adding the Etendo core library.
This causes problems because the 'Etendo core' contains classes that are already defined in the 'Gradle project'""")
class CopyConfigDirFromEtendoCoreJarTest extends EtendoCoreJarSpecificationTest {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
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
        def dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies","-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS
        assert dependenciesTaskResult.output.contains(CORE)

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
