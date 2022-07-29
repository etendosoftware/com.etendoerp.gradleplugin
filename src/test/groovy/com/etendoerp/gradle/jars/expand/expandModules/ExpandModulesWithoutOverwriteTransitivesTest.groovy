package com.etendoerp.gradle.jars.expand.expandModules

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Issue("EPL-190")
@Title("Expanding a module with transitive dependency does not overwrites the sources")
@Narrative("""
(Sources not supporting JARs)

When a user is working with module A in sources, and wants to expand the module C
which depends on A, by default A would be overwritten. The user can specify a flag
in the plugin extension to prevent this behavior.

""")
class ExpandModulesWithoutOverwriteTransitivesTest extends EtendoCoreResolutionSpecificationTest {

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST_SNAPSHOT
    }

    def "Expanding a module with transitive dependencies already in sources"() {
        given: "A Etendo core '#coreType'"
        addRepositoryToBuildFile(SNAPSHOT_REPOSITORY_URL)
        addRepositoryToBuildFile(getCoreRepo())

        Map pluginVariables = [
                "coreVersion" : "'${getCoreVersion()}'",
                "overwriteTransitiveExpandModules": overwriteTransitiveExpandModules,
                ignoreExpandMenu : true,
                "supportJars" : false,
                forceResolution : true
        ]

        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The users expands the module A:1.0.0"
        def moduleA = "com.test.compilationA"

        buildFile << """
            dependencies {
              moduleDeps('com.test:compilationA:1.0.0@zip')
            }
        """

        // TODO: Add flag to ignore menu RDY

        and: "The user runs the 'expandModules' task"
        def expandTaskResult = runTask(":expandModules", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        expandTaskResult.task(":expandModules").outcome == TaskOutcome.SUCCESS

        and: "The module A in the 'modules' dir"
        def modulesLocation = new File(testProjectDir, "modules")

        def moduleALocation = new File(modulesLocation, "${moduleA}")
        assert moduleALocation.exists()

        and: "The user make some work in the module A"
        def newFile = "newFile.txt"
        def newFileLocation = new File(moduleALocation, newFile)
        newFileLocation.createNewFile()
        newFileLocation.write("Test new file")

        when: "The user wants to add a new module C which depends on A"
        def auxBuildFile = ""
        buildFile.text.eachLine {
            if (it.contains("com.test:compilationA")) {
                it = it.replaceAll(".","")
            }
            auxBuildFile += "${it} \n"
        }

        buildFile.text = auxBuildFile

        def moduleC = "com.test.compilationC"
        buildFile << """
            dependencies {
              moduleDeps('com.test:compilationC:1.0.0@zip') {
               transitive = true
              }
            }
        """
        def expandTaskResultExtra = runTask(":expandModules", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        expandTaskResultExtra.task(":expandModules").outcome == TaskOutcome.SUCCESS

        def moduleCLocation = new File(modulesLocation, "${moduleC}")
        assert moduleCLocation.exists()

        then: "The module A will be expanded. overwriting: '#overwriteTransitiveExpandModules'"

        if (overwriteTransitiveExpandModules) {
            assert !newFileLocation.exists()
        } else {
            assert newFileLocation.exists()
        }

        where:
        coreType  | overwriteTransitiveExpandModules | _
        "sources" | false                            | _
        "sources" | true                             | _

    }

}
