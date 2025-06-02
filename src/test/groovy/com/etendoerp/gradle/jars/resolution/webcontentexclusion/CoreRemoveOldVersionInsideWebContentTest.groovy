package com.etendoerp.gradle.jars.resolution.webcontentexclusion

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("Removing old version of the CORE JAR when updating the version")
@Narrative("""
The old version of the CORE JAR should be removed from the WebContent libs
and be replaced with the new one.
""")
@Issue("EPL-115")
class CoreRemoveOldVersionInsideWebContentTest extends EtendoCoreResolutionSpecificationTest {

    @TempDir File testProjectDir

    def coreVersionToInstall = "[1.0.0,)"

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreRepo() {
        return SNAPSHOT_REPOSITORY_URL
    }

    @Override
    String getCoreVersion() {
        return coreVersionToInstall
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    def "Removing old version of CORE JAR when updates"() {
        given: "A Etendo core '#coreType'"
        addRepositoryToBuildFile(getCoreRepo())

        // TODO: Use the release versions

        def oldCoreVersion = "24.4.9"
        def newCoreVersion = ETENDO_LATEST_SNAPSHOT

        coreVersionToInstall = oldCoreVersion

        Map pluginVariables = [
                "coreVersion" : "'${oldCoreVersion}'",
                "forceResolution": true,
                "ignoredArtifacts" : "['com.etendoerp.platform.etendo-core']"
        ]
        loadCore([coreType : "jar", pluginVariables: pluginVariables])

        and: "The user expand the core"
        resolveCore([coreType : "jar", testProjectDir: testProjectDir])

        and: "The version resolved will be the one specified by the user"
        def artifactPropertiesLocation = new File(testProjectDir, "build/etendo/${EtendoArtifactMetadata.METADATA_FILE}")
        assert artifactPropertiesLocation.exists()
        assert artifactPropertiesLocation.text.contains("${oldCoreVersion}")

        and: "The user install the Environment"
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        and: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "After running the 'smartbuild' task, the CORE JAR file will be deployed in the WebContent dir"
        def smartbuildResult = runTask("smartbuild")
        smartbuildResult.task(":smartbuild").outcome  == TaskOutcome.SUCCESS

        def webContentLibs = new File(testProjectDir, "WebContent/WEB-INF/lib")

        def containsCoreOldVersion = false
        def containsCoreNewVersion = false

        webContentLibs.listFiles().each {
            if (it.name.contains("etendo-core-${oldCoreVersion}")) {
                containsCoreOldVersion = true
            }
            if (it.name.contains("etendo-core-${newCoreVersion}")) {
                containsCoreNewVersion = true
            }
        }

        // The WebContent should contain the OLD version
        assert containsCoreOldVersion
        assert !containsCoreNewVersion

        and: "The users wants to update the version of the core in JAR"
        coreVersionToInstall = newCoreVersion

        pluginVariables = [
                "coreVersion" : "'${newCoreVersion}'",
                "forceResolution": true,
                "ignoredArtifacts" : "['com.etendoerp.platform.etendo-core']"
        ]
        loadCore([coreType : "jar", pluginVariables: pluginVariables])

        and: "The users resolves the new core version"
        resolveCore([coreType : "jar", testProjectDir: testProjectDir])

        and: "The resolved version will be the new one specified by the user"
        assert artifactPropertiesLocation.exists()
        assert artifactPropertiesLocation.text.contains("${newCoreVersion}")

        and: "The user runs the 'update.database' task"
        def updateResult = runTask(":update.database")

        and: "The environment is updated correctly"
        updateResult.task(":update.database").outcome == TaskOutcome.SUCCESS

        when: "The users runs the 'smartbuild' task to redeploy the context"
        def reSmartbuildResult = runTask("smartbuild")
        reSmartbuildResult.task(":smartbuild").outcome  == TaskOutcome.SUCCESS

        then: "The old version of the CORE JAR should be deleted and replaced with the new one"

        def _containsCoreOldVersion = false
        def _containsCoreNewVersion = false

        def _webContentLibs = new File(testProjectDir, "WebContent/WEB-INF/lib")

        _webContentLibs.listFiles().each {
            if (it.name.contains("etendo-core-${oldCoreVersion}")) {
                _containsCoreOldVersion = true
            }
            if (it.name.contains("etendo-core-${newCoreVersion}")) {
                _containsCoreNewVersion = true
            }
        }

        // The WebContent should contain ONLY the NEW version
        assert !_containsCoreOldVersion
        assert _containsCoreNewVersion

    }

}
