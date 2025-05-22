package com.etendoerp.gradle.jars.dependencies.buildvalidations

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.TempDir
import spock.lang.Title

@Issue("EPL-314")
@Title("Running the build validations and modulescripts")
class ModuleValidationsTest extends EtendoCoreResolutionSpecificationTest {
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

    def "Running the build validations and modulescripts when core is in JAR or SOURCES"() {
        given: "A user wanting to install a JAR module dependency"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", forceResolution: true,  ignoreDisplayMenu : true]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        String moduleJarDependency = "implementation 'com.test:modulevalidations:1.0.0'"
        String moduleSourceDependency = "moduleDeps 'com.test:modulevalidations:1.0.0@zip'"
        and: "The user adds a JAR module dependency containing build validation files"
        buildFile << """
                repositories {
                    maven {
                        url "https://repo.futit.cloud/repository/etendo-test"
                    }
                }
                dependencies {
                    ${moduleJarDependency}
                }
        """

        and: "The user resolves the module"
        def dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies","-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS

        File buildEtendoModules = new File(testProjectDir, "build/etendo/modules")
        File validationModule = new File(buildEtendoModules, "com.test.modulevalidations")
        assert validationModule.exists()

        when: "The user install the Etendo core environment."
        def setupResult = runTask("setup")
        def installResult = runTask("install")

        then: "The environment will be installed correctly"
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        and: "The install will contain the build validation code execution (the modulescript of the module will be executed (import sample data task))"
        installResult.output.contains("ModuleScriptTest modulevalidations")

        // run the build validations
        and: "The user runs the update.database task (the build validation will be executed)"
        def updateTaskResult = runTask(":update.database")
        updateTaskResult.task(":update.database").outcome == TaskOutcome.SUCCESS
        assert updateTaskResult.output.contains("BuildValidationTest modulevalidations")
        assert updateTaskResult.output.contains("ModuleScriptTest modulevalidations")

        when: "The user replaces the dependency in JAR for the sources"
        buildFile.text = buildFile.text.replace(moduleJarDependency, moduleSourceDependency)

        and: "The user runs the expandModules task"
        def expandTaskResult = runTask(":expandModules")
        assert expandTaskResult.task(":expandModules").outcome == TaskOutcome.SUCCESS

        File jarFileLocation = new File(testProjectDir, "build/etendo/modules/com.test.modulevalidations")
        assert !jarFileLocation.exists()

        File sourceFileLocation = new File(testProjectDir, "modules/com.test.modulevalidations")
        assert sourceFileLocation.exists()

        and: "The user runs the update.database task"
        def rerunUpdateTaskResult = runTask(":update.database")

        then: "The update will finish successfully"
        rerunUpdateTaskResult.task(":update.database").outcome == TaskOutcome.SUCCESS
        assert rerunUpdateTaskResult.output.contains("BuildValidationTest modulevalidations")
        assert rerunUpdateTaskResult.output.contains("ModuleScriptTest modulevalidations")

        where:
        coreType  | _
        "sources" | _
        "jar"     | _
    }

}
