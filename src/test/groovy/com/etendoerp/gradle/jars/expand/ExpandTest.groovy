package com.etendoerp.gradle.jars.expand

import com.etendoerp.consistency.EtendoArtifactsConsistencyContainer
import com.etendoerp.gradle.jars.core.coreinsources.CoreUtils
import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.TempDir
import spock.lang.Title

@Issue("EPL-190")
@Title("When running the expand task the core and modules are expanded")
class ExpandTest extends EtendoCoreResolutionSpecificationTest {

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST_SNAPSHOT
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    def "Running the expand tasks the core and modules"() {
        given: "A user wanting to expand a Etendo environment"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)
        // TODO: Add flag to ignore menu
        Map pluginVariables = [
                "coreVersion" : "'${getCoreVersion()}'",
                ignoreExpandMenu : true
        ]
        loadCore([coreType : "sources", pluginVariables: pluginVariables])

        and: "The user adds some dependencies with moduleDeps to work with sources"
        def moduleGroup = "com.test"
        def moduleArtifact = "moduleAextract"
        def moduleVersion = "1.0.0"
        def moduleName = "${moduleGroup}.${moduleArtifact}"

        buildFile << """
            dependencies {
              moduleDeps('${moduleGroup}:${moduleArtifact}:${moduleVersion}@zip') {
                transitive = true
              }
            }
        """

        when: "The user runs the expand task"
        def expandTaskResult = runTask(":expand", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")

        then: "The Core and modules should be expanded"
        expandTaskResult.task(":expand").outcome == TaskOutcome.SUCCESS
        // Core verification
        def srcCoreLocation = new File(testProjectDir, "src-core")
        assert srcCoreLocation.exists()

        def modulesCoreLocation = new File(testProjectDir, "modules_core")
        assert modulesCoreLocation.exists()

        // Modules verification
        def modulesLocation = new File(testProjectDir, "modules")
        def sourceModuleLocation = new File(modulesLocation, moduleName)
        assert sourceModuleLocation.exists()

        when: "The user install the environment"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        and: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        then: "The core and modules should be installed"
        def installedModules = CoreUtils.getMapOfModules(getDBConnection())
        // Core verification
        String coreModule = EtendoArtifactsConsistencyContainer.CORE_MODULE
        def coreModuleInstalled = installedModules.get(coreModule)
        assert coreModuleInstalled

        // Module verification
        String sourceModuleName = "${moduleName}"
        def sourceModule = installedModules.get(sourceModuleName)
        assert sourceModule
        assert sourceModule.get("version") == "1.0.0"

    }

}
