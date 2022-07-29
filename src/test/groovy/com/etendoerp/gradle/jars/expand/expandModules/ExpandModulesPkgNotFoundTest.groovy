package com.etendoerp.gradle.jars.expand.expandModules

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.legacy.modules.expand.ExpandUtils
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Issue("EPL-190")
@Title("Running the expandModules with a package not defined")
@Narrative("""
When the user runs the expandModules passing the flag '-Ppkg=<javapackage>' and the package is not found,
then a error is throw.
""")
class ExpandModulesPkgNotFoundTest extends EtendoCoreResolutionSpecificationTest {

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

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", ignoreExpandMenu : true, forceResolution : true]
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

        // TODO: Add flag to ignore menu Rdy

        when: "The user runs the expandModule task"
        def expandTaskResult = runTask(":expandModules", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        expandTaskResult.task(":expandModules").outcome == TaskOutcome.SUCCESS

        then: "The dependencies should be extracted in the 'modules' dir"
        def modulesLocation = new File(getTestProjectDir(), "modules")

        def moduleLocation = new File(modulesLocation, moduleName)
        assert moduleLocation.exists()

        def extraModuleLocation = new File(modulesLocation, extraModuleName)
        assert extraModuleLocation.exists()

        when: "The user runs again the expandModule task passing the package of the module to expand that NOT exists."
        def pkgToExpand = "-Ppkg=com.test.notfound"

        def success = true
        def exception = null
        def taskResult = null

        try {
            taskResult = runTask(":expandModules", "${pkgToExpand}" ,"-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        } catch (Exception e) {
            success = false
            exception = e
        }

        then: "The expand modules task will fail"
        assert !success
        assert exception
        assert exception.message.contains(ExpandUtils.MODULE_NOT_FOUND_MESSAGE)

        where:
        coreType  | _
        "jar"     | _
        "sources" | _

    }

}
