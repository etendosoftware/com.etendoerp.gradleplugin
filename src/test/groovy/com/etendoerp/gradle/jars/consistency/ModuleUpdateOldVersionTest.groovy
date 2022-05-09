package com.etendoerp.gradle.jars.consistency

import com.etendoerp.consistency.EtendoArtifactsConsistencyContainer
import com.etendoerp.gradle.jars.core.coreinsources.CoreUtils
import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Issue("EPL-123")
@Title("Update module transitive version")
@Narrative("""
When a transitive module in JARs is updated, if the version is older to the current installed, 
then a error should be throw, unless the user specifies to ignore the module.
""")
class ModuleUpdateOldVersionTest extends EtendoCoreResolutionSpecificationTest {

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return ETENDO_21q1_SNAPSHOT
    }

    @Override
    String getDB() {
        return "ModuleUpdateOldVersionTest".toLowerCase()
    }

    def "Updating an old version of a module in JARs"() {
        given: "A user with an installed environment"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map<Object, Object> pluginVariables = ["coreVersion" : "'${getCoreVersion()}'"]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The users installs the module B:1.0.0 which depends on A:[1.0.0, 1.0.1]"
        buildFile << """
            dependencies {
              implementation 'com.test:moduleBextract:1.0.0'
            }
        """

        def dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS

        String moduleA = "com.test.moduleAextract"
        String moduleB = "com.test.moduleBextract"
        String moduleC = "com.test.moduleCextract"

        String currentAversion = "1.0.1"
        String newAversion = "1.0.0"

        def locationModuleA = new File(testProjectDir, "build/etendo/modules/${moduleA}")
        assert locationModuleA.exists()

        def artifactPropertiesFile = new File(locationModuleA, "${EtendoArtifactMetadata.METADATA_FILE}")
        assert artifactPropertiesFile.exists()
        assert artifactPropertiesFile.text.contains(currentAversion)


        and: "The user install the Etendo core environment along with the dependency."
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        and: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "The modules B:1.0.0 and A:1.0.1 are installed in the database"

        def modulesInstalled = CoreUtils.getMapOfModules(getDBConnection())

        def installedModuleA = modulesInstalled.get(moduleA)
        def installedModuleB = modulesInstalled.get(moduleB)

        assert installedModuleA.version == "1.0.1"
        assert installedModuleB.version == "1.0.0"

        when: "The user specifies the module C:1.0.0 which depends on A:[1.0.0]"

        buildFile << """
            dependencies {
              implementation 'com.test:moduleCextract:1.0.0'
            }
        """

        and: "The users triggers the extraction phase but the version is MINOR to the installed one, A error should be throw before the extraction "

        boolean success = true
        Exception exception  = null
        dependenciesTaskResult = null
        try {
            dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        } catch (UnexpectedBuildFailure ignored) {
            exception = ignored
            success = false
        }

        assert !success
        assert exception
        assert exception.message.contains("${EtendoArtifactsConsistencyContainer.VALIDATION_ERROR_MESSAGE} '${moduleA}'")

        and: "The user specifies in the ‘ignoreArtifacts’ the A module"

        String ignoredArtifact =  "${moduleA}"
        pluginVariables.put("ignoredArtifacts", "['${ignoredArtifact}']")
        changeExtensionPluginVariables(pluginVariables)

        and: "The users triggers the extraction"
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

        and: "The module is extracted correctly in the build/etendo/modules dir, with the updated version"
        locationModuleA = new File(testProjectDir, "build/etendo/modules/${moduleA}")
        assert locationModuleA.exists()

        artifactPropertiesFile = new File(locationModuleA, "${EtendoArtifactMetadata.METADATA_FILE}")
        assert artifactPropertiesFile.exists()
        assert artifactPropertiesFile.text.contains(newAversion)

        and: "The users update the environment"
        def updateResult = runTask(":update.database")

        and: "The environment will be updated correctly"
        updateResult.task(":update.database").outcome == TaskOutcome.SUCCESS

        then: "The module C:1.0.0 will be installed"
        def modulesUpdated = CoreUtils.getMapOfModules(getDBConnection())
        def installedModuleC = modulesUpdated.get(moduleC)

        assert installedModuleC
        assert installedModuleC.version == "1.0.0"

        and: "The module A will be updated from 1.0.1 to 1.0.0"
        def updatedModuleA = modulesUpdated.get(moduleA)
        assert updatedModuleA.version == "${newAversion}"

        where:
        coreType  | _
        "sources" | _
        "jar"     | _
    }

}
