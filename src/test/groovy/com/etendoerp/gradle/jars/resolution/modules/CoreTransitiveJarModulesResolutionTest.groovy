package com.etendoerp.gradle.jars.resolution.modules

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("Adding two modules (A and C) with a dependency on (B), resolves the matched version of B")
@Narrative("""
    Given the next graph of dependencies
    
    A:1.0.0
    |   |--- B:[1.0.0, 1.0.1]
    | 
    C:1.0.0
        |--- B:[1.0.1, 1.0.2]
         
   The module B with version 1.0.1 should be resolved.
""")
class CoreTransitiveJarModulesResolutionTest extends EtendoCoreResolutionSpecificationTest {

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return "1.0.0"
    }

    def "Resolving the correct version of a transitive module"() {
        given: "A Etendo core '#coreType'"

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'"]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        when: "The user adds two dependencies (A and C) depending of different versions of (B)"
        buildFile << """
        dependencies {
          implementation('com.test:transitiveA:1.0.0')
          implementation('com.test:transitiveC:1.0.0')
        }
        """

        and: "The user runs the 'dependencies' task"
        def dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS

        then: "The JAR module B will be extracted in the 'build/etendo' dir "
        File moduleLocation = new File(testProjectDir, "build/etendo/modules/com.test.transitiveB")
        assert moduleLocation.exists()

        and: "The extracted version will be the matched between A and C"
        File artifactProperties = new File(moduleLocation, "${EtendoArtifactMetadata.METADATA_FILE}")
        assert artifactProperties.exists()
        assert artifactProperties.text.contains("1.0.1")

        where:
        coreType  | _
        "jar"     | _
        "sources" | _
    }

}
