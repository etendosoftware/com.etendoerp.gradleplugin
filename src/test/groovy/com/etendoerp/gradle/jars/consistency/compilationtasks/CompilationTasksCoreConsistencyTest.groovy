package com.etendoerp.gradle.jars.consistency.compilationtasks

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
@Title("The core consistency verification is ran when is updated")
@Narrative("""
    Running any compilation task when the core in JAR is updated (only extracted but not in the database),
    then a error should be throw.
""")
class CompilationTasksCoreConsistencyTest extends EtendoCoreResolutionSpecificationTest {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    def coreVersionToInstall = "24.4.3"

    @Override
    String getCoreVersion() {
        return coreVersionToInstall
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    def "Running compilation tasks when the core in JARs is updated"() {
        given: "The user with an installed version of the CORE in JARs"
        def currentCoreVersion = "24.4.3"
        def currentCoreVersionXML = "24.4.3"

        def newCoreVersion = ETENDO_LATEST_SNAPSHOT
        def newCoreVersionXML = ETENDO_LATEST_SNAPSHOT

        coreVersionToInstall = currentCoreVersion

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
        assert installedCore.get("version") == "${currentCoreVersionXML}"

        and: "The users wants to update the version of the core in JAR"
        coreVersionToInstall = newCoreVersion
        buildFile.text = buildFile.text.replace(currentCoreVersion, newCoreVersion)

        and: "The users triggers the extraction phase"

        def success = true
        def exception  = null
        def dependenciesTaskResult = null
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

        and: "The user tries to run the smartbuild task, fails because the new version is not updated in the database"
        runSmartBuildTask(false, EtendoArtifactsConsistencyContainer.CORE_ARTIFACT_CONSISTENT_ERROR)

        when: "The user update the environment"
        def updateResult = runTask(":update.database")

        and: "The environment will be updated correctly"
        updateResult.task(":update.database").outcome == TaskOutcome.SUCCESS

        then: "The core should be updated in the database"
        def modulesUpdated = CoreUtils.getMapOfModules(getDBConnection())
        String coreUp = "org.openbravo"
        def updatedModule = modulesUpdated.get(coreUp)
        assert updatedModule
        assert updatedModule.get("version") == "${newCoreVersionXML}"

        and: "The user will be able to run the smartbuild task."
        runSmartBuildTask(true)

    }

}
