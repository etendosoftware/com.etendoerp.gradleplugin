package com.etendoerp.gradle.jars.consistency

import com.etendoerp.consistency.EtendoArtifactsConsistencyContainer
import com.etendoerp.gradle.jars.core.coreinsources.CoreUtils
import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

/**
 * This test should use the latest CORE snapshot
 */

@Issue("EPL-123")
@Title("The extraction of an old version of a module in JAR should be omitted")
@Narrative("""
 When updating the version of a module in JAR, if the version is older to the current one installed,
 then a error should be throw. If the user specifies to ignore the module, then the error should not be throw.
""")
class ModuleExtractionVerificationTest extends EtendoCoreResolutionSpecificationTest {

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return ETENDO_LATEST
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    def "Extracting and old version of a module" () {
        given: "A user adding a JAR dependency"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        def group = "com.test"
        def name = "moduleToExtract"

        def currentVersion = "1.0.1"
        def newVersion = "1.0.0"

        buildFile << """
            dependencies {
              implementation '${group}:${name}:${currentVersion}'
            }
        """

        Map<Object, Object> pluginVariables = ["coreVersion" : "'${getCoreVersion()}'"]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The user install the Etendo core environment along with the dependency."
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        and: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "The module should be installed in the database"
        def modulesInstalled = CoreUtils.getMapOfModules(getDBConnection())
        String module = "${group}.${name}"
        def moduleInstalled = modulesInstalled.get(module)

        assert moduleInstalled
        assert moduleInstalled.get("version") == "${currentVersion}"

        when: "The users wants to work with an old version of the same module"
        buildFile.text = buildFile.text.replace("${currentVersion}", "${newVersion}")

        and: "The dependency should not be extracted. A error should be throw."
        //
        boolean success = true
        Exception exception  = null
        def dependenciesTaskResult = null
        try {
            dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        } catch (UnexpectedBuildFailure ignored) {
            exception = ignored
            success = false
        }

        assert !success
        assert exception
        assert exception.message.contains("${EtendoArtifactsConsistencyContainer.VALIDATION_ERROR_MESSAGE} '${group}.${name}'")

        and: "The users specifies in the ignoredArtifacts list the dependency to update"
        String ignoredArtifact =  "${group}.${name}"
        pluginVariables.put("ignoredArtifacts", "['${ignoredArtifact}']")
        changeExtensionPluginVariables(pluginVariables)

        success = true
        exception  = null
        dependenciesTaskResult = null
        try {
            dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        } catch (UnexpectedBuildFailure ignored) {
            exception = ignored
            success = false
        }

        assert success
        assert !exception
        assert dependenciesTaskResult
        assert dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE

        and: "The users update the environment"
        def updateResult = runTask(":update.database")

        and: "The environment will be updated correctly"
        updateResult.task(":update.database").outcome == TaskOutcome.SUCCESS

        then: "The module should be updated in the database"
        def modulesUpdated = CoreUtils.getMapOfModules(getDBConnection())
        String moduleUp = "${group}.${name}"
        def updatedModule = modulesUpdated.get(moduleUp)

        assert updatedModule
        assert updatedModule.get("version") == "${newVersion}"

        where:
        coreType  | _
        "sources" | _
        "jar"     | _
    }

}
