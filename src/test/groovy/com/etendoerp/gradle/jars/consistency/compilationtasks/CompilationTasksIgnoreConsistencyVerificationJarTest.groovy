package com.etendoerp.gradle.jars.consistency.compilationtasks

import com.etendoerp.consistency.EtendoArtifactsConsistencyContainer
import com.etendoerp.gradle.jars.core.coreinsources.CoreUtils
import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.legacy.ant.ConsistencyVerification
import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

/**
 * This test should use the latest CORE - JAR version
 */

@Issue("EPL-123")
@Title("Ignoring the consistency verification - JAR Core")
@Narrative("""
The user can ignore the consistency verification using
the 'ignoreConsistencyVerification' plugin extension variable,
the '--PignoreConsistency' and the '-Dlocal=no' flags.
Testing with JAR core type.
""")
class CompilationTasksIgnoreConsistencyVerificationJarTest extends EtendoCoreResolutionSpecificationTest {
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
        return "jar_consistency_test"
    }

    def "The user ignores the consistency verification with jar core"() {
        given: "A user wanting to install a JAR module dependency"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map<Object, Object> pluginVariables = ["coreVersion": "'${getCoreVersion()}'"]
        loadCore([coreType: "jar", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType: "jar", testProjectDir: testProjectDir])

        and: "The user install the Etendo core environment."
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        and: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "The user runs the smartbuild task"
        def smartbuildResult = runTask("smartbuild")
        smartbuildResult.task(":smartbuild").outcome == TaskOutcome.SUCCESS

        and: "The user specifies a JAR module dependency to install"
        String moduleA = "com.test.moduleAextract"

        buildFile << """
            dependencies {
              implementation 'com.test:moduleAextract:1.0.0'
            }
        """

        and: "The user resolves the dependency"
        def dependenciesTaskResult = runTask(":dependencies", "--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS

        def locationModuleA = new File(testProjectDir, "build/etendo/modules/${moduleA}")
        assert locationModuleA.exists()

        def artifactPropertiesFile = new File(locationModuleA, "${EtendoArtifactMetadata.METADATA_FILE}")
        assert artifactPropertiesFile.exists()
        assert artifactPropertiesFile.text.contains("1.0.0")

        and: "The users tries to run the smartbuild task, but it should fail because there is inconsistencies"
        runSmartBuildTask(false, EtendoArtifactsConsistencyContainer.JAR_MODULES_CONSISTENT_ERROR)

        when: "The users adds the plugin extension variable to ignore the consistency verification"
        pluginVariables.put("ignoreConsistencyVerification", true)
        changeExtensionPluginVariables(pluginVariables)

        then: "The user should be able to run the smartbuild task without errors"
        runSmartBuildTask(true)

        when: "The user removes the ignore consistency flag"
        pluginVariables.put("ignoreConsistencyVerification", false)
        changeExtensionPluginVariables(pluginVariables)

        then: "The smartbuild task should fail"
        runSmartBuildTask(false, EtendoArtifactsConsistencyContainer.JAR_MODULES_CONSISTENT_ERROR)

        when: "The users runs the smartbuild task with the -PignoreConsistency flag set to true"
        def ignoreConsistencyFlag = "-P${ConsistencyVerification.IGNORE_CONSISTENCY}=true"

        then: "The smartbuild task should not fail"
        runSmartBuildTask(true, "", ignoreConsistencyFlag)

        when: "The user runs the smartbuild task with the -Dlocal=no"
        def localFlag = "-Dlocal=no"

        then: "The smartbuild task should run successfully"
        runSmartBuildTask(true, "", localFlag)

        and: "The environment should be updated"
        def modulesUpdated = CoreUtils.getMapOfModules(getDBConnection())
        String moduleName = "com.test.moduleAextract"
        def updatedModule = modulesUpdated.get(moduleName)

        assert updatedModule
        assert updatedModule.get("version") == "1.0.0"
    }
}