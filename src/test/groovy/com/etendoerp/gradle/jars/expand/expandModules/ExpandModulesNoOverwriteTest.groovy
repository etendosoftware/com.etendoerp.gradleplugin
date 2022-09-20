package com.etendoerp.gradle.jars.expand.expandModules

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Issue("EPL-190")
@Title("Running the expandModules task with an already source modules is not overwritten")
@Narrative("""
Adding a custom module with the 'moduleDeps' config, and running the expandModules task,
extract the module in the root 'modules' dir. If the user make some changes in the module,
when the user runs again the expandModules task, those changes still remains.
""")
class ExpandModulesNoOverwriteTest extends EtendoCoreResolutionSpecificationTest {

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST_SNAPSHOT
    }

    def "Running expandModules task multiple times"() {
        given: "The users adds a moduleDeps dependency"
        addRepositoryToBuildFile(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", forceResolution: true, ignoreDisplayMenu : true]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        def moduleGroup = "com.test"
        def moduleArtifact = "moduleBextract"
        def moduleVersion = "1.0.0"
        def moduleName = "${moduleGroup}.${moduleArtifact}"

        buildFile << """
            dependencies {
              moduleDeps('${moduleGroup}:${moduleArtifact}:${moduleVersion}@zip') {
                transitive = true
              }
            }
        """

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        when: "The user runs the expandModule task"
        def expandTaskResult = runTask(":expandModules", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        expandTaskResult.task(":expandModules").outcome == TaskOutcome.SUCCESS
//        com.etendoerp.platform:etendo-core:22.2.0.1657972019-20220716.114700-1
        then: "The dependency should be extracted in the 'modules' dir"
        def modulesLocation = new File(getTestProjectDir(), "modules")
        def moduleLocation = new File(modulesLocation, moduleName)
        assert moduleLocation.exists()

        when: "The user makes some changes in the extracted module"
        def newFileText = "new file text"
        def editFileText = "// edit file text"

        // create new file
        def newFile = new File(moduleLocation, "test.txt")
        newFile.createNewFile()
        newFile.text = newFileText

        // edit file
        def buildLocationFile = new File(moduleLocation, "build.gradle")
        buildLocationFile.text = buildLocationFile.text + "\n${editFileText}"

        def developmentList = inDevelopmentList ? "['${moduleGroup}.${moduleArtifact}']" : "[]"

        Map pluginVariablesUpdate = [
                "coreVersion" : "'${getCoreVersion()}'",
                forceResolution: true,
                ignoreDisplayMenu : true,
                sourceModulesInDevelopment : developmentList.toString()
        ]
        changeExtensionPluginVariables(pluginVariablesUpdate)

        and: "The user runs again the expandModule task"
        def expandTaskResultRerun = runTask(":expandModules", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        expandTaskResultRerun.task(":expandModules").outcome == TaskOutcome.SUCCESS

        def modulesLocationRerun = new File(getTestProjectDir(), "modules")
        def moduleLocationRerun = new File(modulesLocationRerun, moduleName)
        assert moduleLocationRerun.exists()

        // new file
        def newFileRerun = new File(moduleLocationRerun, "test.txt")
        // edit file
        def buildLocationFileRerun = new File(moduleLocationRerun, "build.gradle")

        then: "The changes remains if the module is in de development list, otherwise are overwritten"

        if (!inDevelopmentList) {
            // The changes are overwritten
            assert !newFileRerun.exists()

        } else {
            // The changes still remain
            assert newFileRerun.exists()
            assert newFileRerun.text.contains(newFileText)

            assert buildLocationFileRerun.exists()
            assert buildLocationFileRerun.text.contains(editFileText)
        }

        where:
        coreType  | inDevelopmentList | _
        "sources" | true              | _
        "jar"     | true              | _
        "sources" | false             | _
        "jar"     | false             | _

    }

}
