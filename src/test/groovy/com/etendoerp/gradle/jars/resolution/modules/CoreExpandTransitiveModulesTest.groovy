package com.etendoerp.gradle.jars.resolution.modules

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("Expanding transitive modules should extract the correct version")
@Narrative("""
Given the next graph of dependencies
    
    A:1.0.0
    |   |--- B:[1.0.0, 1.0.1]
    | 
    C:1.0.0
        |--- B:[1.0.1, 1.0.2]
         
   The module B with version 1.0.1 should be resolved and extracted 
   in Sources or JAR depending on if the core support jars.

""")
class CoreExpandTransitiveModulesTest extends EtendoCoreResolutionSpecificationTest {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return "1.0.0"
    }

    def "Expanding transitive modules extract the correct version"() {
        given: "A Etendo core '#coreType'"

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", supportJars: supportJars]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        when: "The user adds two dependencies (A and C) to be expanded depending of different versions of (B)"
        buildFile << """
            dependencies {
              moduleDeps('com.test:transitiveA:1.0.0@zip') {
                transitive = true
              }
              moduleDeps('com.test:transitiveC:1.0.0@zip') {
                transitive = true
              }
            }
        """

        and: "The user runs the 'expandModules' task"
        def expandTaskResult = runTask(":expandModules", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        expandTaskResult.task(":expandModules").outcome == TaskOutcome.SUCCESS

        then: "The modules A and C should be expanded in the 'modules' dir"
        def modulesLocation = new File(testProjectDir, "modules")

        def moduleALocation = new File(modulesLocation, "com.test.transitiveA")
        assert moduleALocation.exists()

        def moduleCLocation = new File(modulesLocation, "com.test.transitiveC")
        assert moduleCLocation.exists()

       File extractedModuleLocation = null

        if (supportJars) {
            // Refresh the dependencies to trigger the configuration phase and extract the JAR modules
            def dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
            dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS
            extractedModuleLocation = new File(testProjectDir, "build/etendo/modules")
            assert extractedModuleLocation.exists()
        } else {
            extractedModuleLocation = modulesLocation
        }

        and: "The module B should be extracted in the a modules dir"
        def moduleBLocation = new File(extractedModuleLocation, "com.test.transitiveB")
        assert moduleBLocation.exists()

        and: "The module version should be the matched one"
        def artifactProperties = new File(moduleBLocation, "${EtendoArtifactMetadata.METADATA_FILE}")
        assert artifactProperties.exists()
        assert artifactProperties.text.contains("1.0.1")

        where:
        coreType  | supportJars | _
        "sources" | false       | _
        "sources" | true        | _
        "jar"     | true        | _
    }

}
