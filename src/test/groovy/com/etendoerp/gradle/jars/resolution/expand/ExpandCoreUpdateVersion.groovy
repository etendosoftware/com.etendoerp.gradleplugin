package com.etendoerp.gradle.jars.resolution.expand

import com.etendoerp.gradle.jars.core.coreinsources.CoreUtils
import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.TempDir
import spock.lang.Title

@Title("Running the expandCore task to update the core version in SOURCES")
@Issue("EPL-149")
class ExpandCoreUpdateVersion extends EtendoCoreResolutionSpecificationTest {

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    def "Running the expandCore task to update the core in Sources"() {
        given: "A Etendo core '#coreType'"
        addRepositoryToBuildFile(getCoreRepo())

        Map pluginVariables = [
                "coreVersion" : "'11.4.0'",
        ]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user expand the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The version resolved will be the one specified by the user"
        def artifactPropertiesLocation = new File(testProjectDir, "${EtendoArtifactMetadata.METADATA_FILE}")
        assert artifactPropertiesLocation.exists()
        assert artifactPropertiesLocation.text.contains("11.4.0")

        and: "The user install the Environment"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        and: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        def coreJavaPackage = "org.openbravo"

        and: "The environment should contain the core version specified by the user"
        def modules = CoreUtils.getMapOfModules(getDBConnection())
        def coreModule = modules.get(coreJavaPackage)

        assert coreModule
        assert coreModule.get("version") == "3.0.114000"

        when: "The users wants to update the version of the core in Sources"
        pluginVariables = [
                "coreVersion" : "'12.1.0'",
        ]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The users resolves the new core version"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The resolved version will be the new one specified by the user"
        assert artifactPropertiesLocation.exists()
        assert artifactPropertiesLocation.text.contains("12.1.0")

        and: "The user runs the 'update.database' task"
        def updateResult = runTask(":update.database")

        then: "The environment will be updated correctly"
        updateResult.task(":update.database").outcome == TaskOutcome.SUCCESS

        and: "The environment should contain the core version specified by the user"
        def modulesUpdated = CoreUtils.getMapOfModules(getDBConnection())
        def coreModuleUpdated = modulesUpdated.get(coreJavaPackage)

        assert coreModuleUpdated
        assert coreModuleUpdated.get("version") == "3.0.121000"

        where:
        coreType  | _
        "sources" | _
    }

}
