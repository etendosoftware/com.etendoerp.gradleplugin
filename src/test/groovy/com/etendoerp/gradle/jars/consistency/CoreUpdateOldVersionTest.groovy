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
 * Is not necessary to use the latest core version for this test.
 * The CORE dependency should be obtained from the "https://repo.futit.cloud/repository/etendo-resolution-test/" repo
 */

@Issue("EPL-123")
@Title("Update the core in JARs to an old version")
@Narrative("""
When the users updates the version of the installed core to an old version, a
error should be throw, except if is ignored.
""")
class CoreUpdateOldVersionTest extends EtendoCoreResolutionSpecificationTest {

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    def coreVersionToInstall = "23.2.0"

    @Override
    String getCoreVersion() {
        return coreVersionToInstall
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    def "Update the core JAR to an old version"() {
        given: "The user with an installed version of the CORE in JARs"
        def currentCoreVersion = ETENDO_LATEST
        def currentCoreVersionXML = ETENDO_LATEST

        def newCoreVersion = "23.2.0"
        def newCoreVersionXML = "23.2.0"

        coreVersionToInstall = currentCoreVersion

        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'"]
        loadCore([coreType : "jar", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "jar", testProjectDir: testProjectDir])

        and: "The user install the Etendo core environment."
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        and: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "The core current version will be installed in the database"
        def modulesInstalled = CoreUtils.getMapOfModules(getDBConnection())
        String coreInstalled = "org.openbravo"
        def installedCore = modulesInstalled.get(coreInstalled)

        assert installedCore
        assert installedCore.get("version") == currentCoreVersion

        when: "The users wants to update the version of the core in JAR"
        coreVersionToInstall = newCoreVersion
        buildFile.text = buildFile.text.replace(currentCoreVersion, newCoreVersion)

        and: "The users triggers the extraction phase, failing on extracting the core JAR"

        boolean success = true
        Exception exception  = null
        def dependenciesTaskResult = null
        try {
            dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}", "-DgithubUser=${args.get("githubUser")}", "-DgithubToken=${args.get("githubToken")}")
        } catch (UnexpectedBuildFailure ignored) {
            exception = ignored
            success = false
        }

        assert !success
        assert exception
        assert exception.message.contains("${EtendoArtifactsConsistencyContainer.VALIDATION_ERROR_MESSAGE}")

        and: "The users specifies in the ignoredArtifacts list the dependency to update"
        String ignoredArtifact =  "com.etendoerp.platform.etendo-core"
        pluginVariables.put("ignoredArtifacts", "['${ignoredArtifact}']")
        changeExtensionPluginVariables(pluginVariables)

        success = true
        exception  = null
        dependenciesTaskResult = null
        try {
            dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}", "-DgithubUser=${args.get("githubUser")}", "-DgithubToken=${args.get("githubToken")}")
        } catch (UnexpectedBuildFailure ignored) {
            exception = ignored
            success = false
        }

        assert success
        assert !exception
        assert dependenciesTaskResult
        assert dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE

        and: "The user update the environment"
        def updateResult = runTask(":update.database")

        and: "The environment will be updated correctly"
        updateResult.task(":update.database").outcome == TaskOutcome.SUCCESS

        then: "The core should be updated in the database"
        def modulesUpdated = CoreUtils.getMapOfModules(getDBConnection())
        String coreUp = "org.openbravo"
        def updatedModule = modulesUpdated.get(coreUp)

        assert updatedModule
        assert updatedModule.get("version") == "${newCoreVersionXML}"

    }

}
