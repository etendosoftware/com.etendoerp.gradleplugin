package com.etendoerp.gradle.jars.expand.expandModules

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Issue("EPL-190")
@Title("Running expandModules task with a custom package")
@Narrative("""
Running the expandModules task with a custom packaged passed by command line, only expand that module.
""")
class ExpandModulesPkgFlagTest extends EtendoCoreResolutionSpecificationTest {

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST
    }

    def "Running expandModules task multiple times"() {
        given: "The users adds a moduleDeps dependency"
        addRepositoryToBuildFile(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", ignoreDisplayMenu : true, forceResolution : true]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        def moduleGroup = "com.test"
        def moduleArtifact = "moduleBextract"
        def moduleVersion = "1.0.0"
        def moduleName = "${moduleGroup}.${moduleArtifact}"

        def extraModuleGroup = "com.test"
        def extraModuleArtifact = "moduleAextract"
        def extraModuleVersion = "1.0.0"
        def extraModuleName = "${extraModuleGroup}.${extraModuleArtifact}"

        buildFile << """
            dependencies {
              moduleDeps('${moduleGroup}:${moduleArtifact}:${moduleVersion}@zip') {
                transitive = true
              }
              moduleDeps('${extraModuleGroup}:${extraModuleArtifact}:${extraModuleVersion}@zip') {
                transitive = true
              }
            }
        """

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        when: "The user runs the expandModule task"
        def expandTaskResult = runTask(":expandModules", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        expandTaskResult.task(":expandModules").outcome == TaskOutcome.SUCCESS

        then: "The dependencies should be extracted in the 'modules' dir"
        def modulesLocation = new File(getTestProjectDir(), "modules")

        def moduleLocation = new File(modulesLocation, moduleName)
        assert moduleLocation.exists()

        def extraModuleLocation = new File(modulesLocation, extraModuleName)
        assert extraModuleLocation.exists()

        when: "The user makes some changes in the modules"
        def newFileText = "new file text"
        def editFileText = "// edit file text"

        // create new file
        def newFile = new File(moduleLocation, "test.txt")
        newFile.createNewFile()
        newFile.text = newFileText

        // edit file
        def buildLocationFile = new File(moduleLocation, "build.gradle")
        buildLocationFile.text = buildLocationFile.text + "\n${editFileText}"

        // create new file extra
        def newFileExtraModule = new File(extraModuleLocation, "test.txt")
        newFileExtraModule.createNewFile()
        newFileExtraModule.text = newFileText

        // edit file extra
        def buildLocationFileExtraModule = new File(extraModuleLocation, "build.gradle")
        buildLocationFileExtraModule.text = buildLocationFileExtraModule.text + "\n${editFileText}"

        and: "The user runs again the expandModule task passing the package of the module to expand"
        def pkgToExpand = "-Ppkg=${extraModuleName}"

        def expandTaskResultRerun = runTask(":expandModules", "${pkgToExpand}" ,"-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        expandTaskResultRerun.task(":expandModules").outcome == TaskOutcome.SUCCESS

        then: "The files in from the pkg passed in the command line will be overwritten"
        def modulesLocationRerun = new File(getTestProjectDir(), "modules")
        def extraModuleLocationRerun = new File(modulesLocationRerun, extraModuleName)

        def newFileExtraModuleRerun = new File(extraModuleLocationRerun, "test.txt")
        assert !newFileExtraModuleRerun.exists()

        and: "The files from the other module should still remain"
        def moduleLocationRerun = new File(modulesLocationRerun, moduleName)

        def newFileRerun = new File(moduleLocationRerun, "test.txt")
        assert newFileRerun.exists()

        def buildLocationFileRerun = new File(moduleLocationRerun, "build.gradle")
        assert buildLocationFileRerun.text.contains(editFileText)

        where:
        coreType  | _
        "sources" | _
        "jar"     | _
    }

}
