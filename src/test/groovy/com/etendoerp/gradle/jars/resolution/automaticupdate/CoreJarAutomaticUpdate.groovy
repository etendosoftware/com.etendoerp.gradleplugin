package com.etendoerp.gradle.jars.resolution.automaticupdate

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("The core in JAR is updated automatically")
@Narrative("""
The users adds a new dependency which depends on different version of the current Core.
The core in JAR should be updated automatically.

The new dependency should also be updated.
""")
class CoreJarAutomaticUpdate extends EtendoCoreResolutionSpecificationTest{
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return "[21.4.0, 22.1.0]"
    }

    def "Core JAR version updates automatically"() {
        given: "A Etendo core '#coreType'"
        addRepositoryToBuildFile(getCoreRepo())

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'"]
        loadCore([coreType: "jar", pluginVariables: pluginVariables])

        and: "The users adds a module dependency in JARs dependsOnCoreA:1.0.0 depending on CORE:[21.4.0, 22.1.0)"
        buildFile << """
        dependencies {
            implementation('com.test:dependsOnCoreA:1.0.0')  
        }
        """

        and: "The user resolves the core"
        resolveCore([coreType : "jar", testProjectDir: testProjectDir])

        and: "The core version resolved is CORE:[21.4.0] (matched with dependsOnCoreA)"
        def coreJarLocation = new File(testProjectDir, "build/etendo")
        assert coreJarLocation.exists()

        def artifactPropertiesFile = new File(coreJarLocation, "${EtendoArtifactMetadata.METADATA_FILE}")
        assert artifactPropertiesFile.exists()
        assert artifactPropertiesFile.text.contains("21.4.0")

        when: "The users adds a new version of the module A:1.0.1 (depending on CORE:[22.1])"
        buildFile << """
        dependencies {
            implementation('com.test:dependsOnCoreA:1.0.1')  
        }
        """

        then: "The version of the core should be updated to CORE:[22.1]"
        def dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS

        assert artifactPropertiesFile.exists()
        assert artifactPropertiesFile.text.contains("22.1.0")

        and: "The version of the module A also should be updated to 1.0.1"
        def moduleALocation = new File(coreJarLocation, "modules/com.test.dependsOnCoreA")
        assert moduleALocation.exists()

        def propertiesFile = new File(moduleALocation, "${EtendoArtifactMetadata.METADATA_FILE}")
        assert propertiesFile.exists()
        assert  propertiesFile.text.contains("1.0.1")

    }

}
